package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GarminPythonClient {

    private final ObjectMapper objectMapper;

    @Value("${garmin.python.script-path:scripts/garmin_sync.py}")
    private String scriptPath;

    @Value("${garmin.python.timeout-seconds:120}")
    private long timeoutSeconds;

    @Value("${garmin.token-dir:data/garmin-tokens}")
    private String tokenDir;

    public SyncResult fetch(Long userId, String email, String password, LocalDate fromDate, LocalDate toDate, DataType dataType) {
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(scriptPath);
        command.add(fromDate.toString());
        command.add(toDate.toString());
        command.add(dataType.name().toLowerCase());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        // 자격증명은 프로세스 인자(ps로 노출) 대신 환경변수로 전달
        Map<String, String> env = pb.environment();
        env.put("GARMIN_EMAIL", email);
        env.put("GARMIN_PASSWORD", password);
        Path tokenStore = resolveTokenStore(userId, email);
        if (tokenStore != null) {
            env.put("GARMIN_TOKENSTORE", tokenStore.toString());
        }

        try {
            log.info("Starting Garmin sync: {} from {} to {}", dataType, fromDate, toDate);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line);
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line);
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Garmin sync timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String err = stderr.toString().trim();
                if (err.isEmpty()) err = stdout.toString().trim();
                throw new RuntimeException("Garmin sync failed: " + err);
            }

            JsonNode root = objectMapper.readTree(stdout.toString());
            if (root.has("error")) {
                throw new RuntimeException("Garmin sync error: " + root.get("error").asText());
            }

            return new SyncResult(root);
        } catch (Exception e) {
            log.error("Garmin sync error", e);
            throw new RuntimeException("Garmin sync failed: " + e.getMessage(), e);
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

    public enum DataType {
        ACTIVITIES, HEALTH, SLEEP, ALL
    }

    public record SyncResult(JsonNode data) {
    }
}
