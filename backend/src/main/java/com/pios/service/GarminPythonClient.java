package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * garmin_sync.py 실행기.
 * <ul>
 *   <li>stdout/stderr를 별도 스레드로 동시에 비워 파이프 버퍼가 차서 멈추는(deadlock) 일을 막는다.</li>
 *   <li>타임아웃은 청크 일수에 비례(base + perDay*days, 최대 max)하며 초과 시 프로세스 트리를 강제 종료한다.</li>
 *   <li>실패 시 stderr 끝부분을 로그로 남기고 {@link GarminFetchException}(kind 포함)을 던진다.</li>
 * </ul>
 */
@Slf4j
@Component
public class GarminPythonClient {

    private static final int STDERR_TAIL_BYTES = 8 * 1024;
    private static final long MAX_STDOUT_BYTES = 256L * 1024 * 1024;

    private final ObjectMapper objectMapper;

    @Value("${garmin.python.executable:python3}")
    private String executable = "python3";

    @Value("${garmin.python.script-path:scripts/garmin_sync.py}")
    private String scriptPath = "scripts/garmin_sync.py";

    @Value("${garmin.python.timeout-base-seconds:120}")
    private long timeoutBaseSeconds = 120;

    @Value("${garmin.python.timeout-per-day-seconds:30}")
    private long timeoutPerDaySeconds = 30;

    @Value("${garmin.python.timeout-max-seconds:1800}")
    private long timeoutMaxSeconds = 1800;

    @Value("${garmin.token-dir:data/garmin-tokens}")
    private String tokenDir = "data/garmin-tokens";

    public GarminPythonClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 청크 기간에 대한 프로세스 타임아웃 */
    public Duration timeoutFor(LocalDate fromDate, LocalDate toDate) {
        long days = Math.max(1, ChronoUnit.DAYS.between(fromDate, toDate) + 1);
        long seconds = Math.min(timeoutMaxSeconds, timeoutBaseSeconds + timeoutPerDaySeconds * days);
        return Duration.ofSeconds(seconds);
    }

    public SyncResult fetch(Long userId, String email, String password, LocalDate fromDate, LocalDate toDate, DataType dataType) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add(scriptPath);
        command.add(fromDate.toString());
        command.add(toDate.toString());
        command.add(dataType.name().toLowerCase());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        // 자격증명은 프로세스 인자(ps로 노출) 대신 환경변수로 전달
        Map<String, String> env = pb.environment();
        env.put("GARMIN_EMAIL", email != null ? email : "");
        env.put("GARMIN_PASSWORD", password != null ? password : "");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONUNBUFFERED", "1");
        Path tokenStore = userId != null && email != null ? resolveTokenStore(userId, email) : null;
        if (tokenStore != null) {
            env.put("GARMIN_TOKENSTORE", tokenStore.toString());
        }

        Duration timeout = timeoutFor(fromDate, toDate);
        log.info("Starting Garmin fetch: {} {}..{} (timeout {}s)", dataType, fromDate, toDate, timeout.toSeconds());

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new GarminFetchException(GarminFetchException.Kind.SCRIPT_ERROR,
                    "Failed to start Garmin script: " + e.getMessage(), e);
        }
        // 입력은 사용하지 않으므로 즉시 닫는다
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // ignore
        }

        StreamDrainer stdout = new StreamDrainer(process.getInputStream(), MAX_STDOUT_BYTES, false, "garmin-stdout");
        StreamDrainer stderr = new StreamDrainer(process.getErrorStream(), STDERR_TAIL_BYTES, true, "garmin-stderr");
        stdout.start();
        stderr.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            throw new GarminFetchException(GarminFetchException.Kind.TIMEOUT, "Garmin fetch interrupted", e);
        }
        if (!finished) {
            destroyTree(process);
            stdout.awaitQuietly(2000);
            stderr.awaitQuietly(2000);
            log.warn("Garmin fetch timed out after {}s ({}..{}); stderr tail: {}",
                    timeout.toSeconds(), fromDate, toDate, stderr.text());
            throw new GarminFetchException(GarminFetchException.Kind.TIMEOUT,
                    "Garmin sync timed out after " + timeout.toSeconds() + " seconds");
        }

        stdout.awaitQuietly(10_000);
        stderr.awaitQuietly(5_000);
        int exitCode = process.exitValue();
        String err = stderr.text().trim();

        if (exitCode != 0) {
            log.warn("Garmin script exited with {} ({}..{}); stderr tail: {}", exitCode, fromDate, toDate, err);
            throw fromScriptError(err, stdout.text().trim(), exitCode);
        }
        if (stdout.truncated()) {
            throw new GarminFetchException(GarminFetchException.Kind.SCRIPT_ERROR,
                    "Garmin script output exceeded " + MAX_STDOUT_BYTES + " bytes");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(stdout.text());
        } catch (IOException e) {
            log.warn("Garmin script produced invalid JSON; stderr tail: {}", err);
            throw new GarminFetchException(GarminFetchException.Kind.SCRIPT_ERROR,
                    "Garmin script returned invalid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new GarminFetchException(GarminFetchException.Kind.SCRIPT_ERROR, "Garmin script returned no data");
        }
        if (root.hasNonNull("error")) {
            throw new GarminFetchException(GarminFetchException.Kind.fromScriptKind(root.path("kind").asText(null)),
                    "Garmin sync error: " + root.get("error").asText());
        }
        if (!err.isEmpty()) {
            log.debug("Garmin script stderr ({}..{}): {}", fromDate, toDate, err);
        }
        return SyncResult.from(root);
    }

    private GarminFetchException fromScriptError(String stderr, String stdout, int exitCode) {
        // 스크립트는 {"error": "...", "kind": "..."}를 stderr 마지막 줄에 출력한다
        String[] lines = stderr.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("{")) {
                try {
                    JsonNode node = objectMapper.readTree(line);
                    if (node.hasNonNull("error")) {
                        return new GarminFetchException(
                                GarminFetchException.Kind.fromScriptKind(node.path("kind").asText(null)),
                                "Garmin sync failed: " + node.get("error").asText());
                    }
                } catch (IOException ignored) {
                    // not JSON
                }
            }
        }
        String detail = !stderr.isEmpty() ? lastLine(stderr) : (stdout.isEmpty() ? "exit code " + exitCode : lastLine(stdout));
        return new GarminFetchException(GarminFetchException.Kind.SCRIPT_ERROR, "Garmin sync failed: " + detail);
    }

    private static String lastLine(String text) {
        String[] lines = text.split("\\R");
        String last = lines[lines.length - 1];
        return last.length() > 500 ? last.substring(0, 500) + "…" : last;
    }

    private static void destroyTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
            // best effort
        }
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 사용자 + 계정(email)별 Garmin 세션 토큰 디렉터리. 계정이 바뀌면 다른 디렉터리를 쓰므로 이전 토큰이 재사용되지 않는다.
     */
    private Path resolveTokenStore(Long userId, String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
            String accountHash = HexFormat.of().formatHex(digest).substring(0, 16);
            Path dir = Path.of(tokenDir, userId + "-" + accountHash).toAbsolutePath();
            Files.createDirectories(dir);
            try {
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX filesystem
            }
            return dir;
        } catch (Exception e) {
            log.warn("Garmin token store unavailable, falling back to credential login: {}", e.getMessage());
            return null;
        }
    }

    /** 스트림을 끝까지 읽는 데몬 스레드. tail=true면 마지막 limit 바이트만 유지한다. */
    private static final class StreamDrainer extends Thread {
        private final InputStream in;
        private final long limit;
        private final boolean tail;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private byte[] tailBuffer = new byte[0];
        private volatile boolean truncated;

        StreamDrainer(InputStream in, long limit, boolean tail, String name) {
            super(name);
            setDaemon(true);
            this.in = in;
            this.limit = limit;
            this.tail = tail;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try (InputStream input = in) {
                int n;
                while ((n = input.read(chunk)) != -1) {
                    synchronized (this) {
                        if (tail) {
                            appendTail(chunk, n);
                        } else if (buffer.size() + n <= limit) {
                            buffer.write(chunk, 0, n);
                        } else {
                            truncated = true; // 계속 읽어서 버리기만 한다 (파이프가 막히지 않도록)
                        }
                    }
                }
            } catch (IOException ignored) {
                // 프로세스 종료로 스트림이 닫힘
            }
        }

        private void appendTail(byte[] chunk, int n) {
            int keep = (int) Math.min(limit, (long) tailBuffer.length + n);
            byte[] next = new byte[keep];
            int fromOld = keep - n;
            if (fromOld > 0) {
                System.arraycopy(tailBuffer, tailBuffer.length - fromOld, next, 0, fromOld);
                System.arraycopy(chunk, 0, next, fromOld, n);
            } else {
                System.arraycopy(chunk, n - keep, next, 0, keep);
            }
            if ((long) tailBuffer.length + n > limit) {
                truncated = true;
            }
            tailBuffer = next;
        }

        void awaitQuietly(long millis) {
            try {
                join(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        boolean truncated() {
            return truncated;
        }

        synchronized String text() {
            byte[] bytes = tail ? tailBuffer : buffer.toByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public enum DataType {
        ACTIVITIES, HEALTH, SLEEP, ALL
    }

    /** 엔드포인트별 비치명 오류 */
    public record EndpointError(String endpoint, String date, String kind, String message) {}

    /** 계속 진행할 수 없는 오류 (auth / rate_limit 등). 수집된 데이터는 함께 반환된다. */
    public record FatalError(String kind, String message, String endpoint) {
        public boolean isAuthOrRateLimit() {
            return "auth".equals(kind) || "rate_limit".equals(kind);
        }
    }

    public record SyncResult(JsonNode data, List<EndpointError> errors, FatalError fatalError) {

        public SyncResult(JsonNode data) {
            this(data, List.of(), null);
        }

        static SyncResult from(JsonNode root) {
            List<EndpointError> errors = new ArrayList<>();
            JsonNode errs = root.path("errors");
            if (errs.isArray()) {
                for (JsonNode e : errs) {
                    errors.add(new EndpointError(
                            e.path("endpoint").asText(null), e.path("date").asText(null),
                            e.path("kind").asText("other"), e.path("message").asText("")));
                }
            }
            FatalError fatal = null;
            JsonNode f = root.path("fatal_error");
            if (f.isObject()) {
                fatal = new FatalError(f.path("kind").asText("other"), f.path("message").asText(""),
                        f.path("endpoint").asText(null));
            }
            return new SyncResult(root, List.copyOf(errors), fatal);
        }
    }
}
