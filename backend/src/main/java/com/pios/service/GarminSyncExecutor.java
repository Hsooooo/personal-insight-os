package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pios.domain.ProviderConnection;
import com.pios.domain.SyncLog;
import com.pios.domain.enums.SyncStatus;
import com.pios.repository.ProviderConnectionRepository;
import com.pios.repository.SyncLogRepository;
import com.pios.security.SecretCryptoService;
import com.pios.service.garmin.ActivityWeatherEnricher;
import com.pios.service.garmin.GarminActivityPersister;
import com.pios.service.garmin.GarminDailyPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Garmin 동기화 오케스트레이션: 청크 분할 → 스크립트 호출(트랜잭션 밖) → 청크 저장(트랜잭션) →
 * 날씨 보강(트랜잭션 밖) → 상태 기록 → 그래프 투영 → {@link GarminSyncFinishedEvent} 발행.
 * <p>
 * 상태 규칙:
 * <ul>
 *   <li>COMPLETED: 모든 청크/엔드포인트 성공</li>
 *   <li>PARTIAL: 일부 엔드포인트/청크 실패 (나머지는 저장됨)</li>
 *   <li>FAILED: 인증 실패·rate limit 등 치명 오류(남은 청크 중단), 또는 모든 청크 실패</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GarminSyncExecutor {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;
    private static final int MAX_CONSECUTIVE_CHUNK_FAILURES = 2;

    private final ProviderConnectionRepository providerRepo;
    private final SyncLogRepository syncLogRepo;
    private final GarminPythonClient pythonClient;
    private final GarminActivityPersister activityPersister;
    private final GarminDailyPersister dailyPersister;
    private final ActivityWeatherEnricher weatherEnricher;
    private final GraphProjectorService graphProjector;
    private final SecretCryptoService secretCrypto;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${sync.chunk-days:7}")
    private int chunkDays = 7;

    @Value("${sync.stale-margin-minutes:15}")
    private long staleMarginMinutes = 15;

    /**
     * 이 기간 동기화가 정상이라면 끝나야 하는 최대 시간 (청크 타임아웃 합 + margin). 이를 넘긴 RUNNING은 stale.
     */
    public Duration maxExpectedDuration(LocalDate fromDate, LocalDate toDate) {
        Duration total = Duration.ofMinutes(staleMarginMinutes);
        if (fromDate == null || toDate == null) {
            return total.plus(pythonClient.timeoutFor(LocalDate.now(), LocalDate.now().plusDays(chunkDays - 1L)));
        }
        for (DateRange chunk : splitIntoChunks(fromDate, toDate, chunkDays)) {
            total = total.plus(pythonClient.timeoutFor(chunk.from(), chunk.to()));
        }
        return total;
    }

    @Async("syncTaskExecutor")
    public void runSyncAsync(Long userId, Long syncLogId, LocalDate fromDate, LocalDate toDate, boolean scheduled) {
        String finalStatus = SyncStatus.FAILED.name();
        try {
            if (syncLogId == null) {
                log.error("syncLogId is null, cannot start sync for user {}", userId);
                return;
            }
            SyncLog syncLog = syncLogRepo.findById(syncLogId).orElse(null);
            if (syncLog == null) {
                log.error("Sync log {} not found for user {}", syncLogId, userId);
                return;
            }
            try {
                ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                        .orElseThrow(() -> new IllegalStateException("Garmin not connected"));
                finalStatus = executeSync(userId, conn, syncLog, fromDate, toDate);
            } catch (Exception e) {
                log.error("Sync failed for user {}", userId, e);
                markFinished(syncLog, SyncStatus.FAILED, new Counts(), e.getMessage());
            }
        } finally {
            publishFinished(userId, syncLogId, finalStatus, scheduled);
        }
    }

    String executeSync(Long userId, ProviderConnection conn, SyncLog syncLog,
                       LocalDate fromDate, LocalDate toDate) {
        String email = extractEmail(conn);
        String password = extractPassword(conn);

        Counts counts = new Counts();
        List<String> issues = new ArrayList<>();
        String fatalMessage = null;
        int failedChunks = 0;
        int consecutiveFailures = 0;

        List<DateRange> chunks = splitIntoChunks(fromDate, toDate, chunkDays);
        for (DateRange chunk : chunks) {
            log.info("Syncing chunk: {} to {} for user {}", chunk.from(), chunk.to(), userId);

            GarminPythonClient.SyncResult result;
            try {
                result = pythonClient.fetch(userId, email, password, chunk.from(), chunk.to(),
                        GarminPythonClient.DataType.ALL);
            } catch (GarminFetchException e) {
                failedChunks++;
                if (e.getKind().isFatal()) {
                    fatalMessage = "[" + e.getKind() + "] " + e.getMessage();
                    break;
                }
                issues.add(chunk + ": " + e.getMessage());
                if (++consecutiveFailures >= MAX_CONSECUTIVE_CHUNK_FAILURES) {
                    issues.add("aborted after " + consecutiveFailures + " consecutive chunk failures");
                    break;
                }
                continue;
            }
            consecutiveFailures = 0;

            boolean persisted = persistChunk(userId, chunk, result.data(), counts, issues);
            if (!persisted) {
                failedChunks++;
            }
            if (!result.errors().isEmpty()) {
                issues.add(chunk + ": " + summarizeEndpointErrors(result.errors()));
            }
            if (result.fatalError() != null) {
                GarminPythonClient.FatalError f = result.fatalError();
                fatalMessage = "[" + f.kind() + "] " + f.message();
                break;
            }
        }

        SyncStatus status;
        String message;
        if (fatalMessage != null) {
            status = SyncStatus.FAILED;
            message = join(fatalMessage, issues);
        } else if (failedChunks >= chunks.size() && counts.total() == 0) {
            status = SyncStatus.FAILED;
            message = join("All chunks failed", issues);
        } else if (!issues.isEmpty()) {
            status = SyncStatus.PARTIAL;
            message = join(null, issues);
        } else {
            status = SyncStatus.COMPLETED;
            message = null;
        }
        markFinished(syncLog, status, counts, message);

        if (status != SyncStatus.FAILED) {
            updateConnectionAfterSync(conn, toDate);
        }
        if (counts.total() > 0) {
            try {
                graphProjector.projectUserData(userId);
            } catch (Exception e) {
                log.error("Graph projection failed for user {}", userId, e);
            }
        }
        log.info("Sync {} for user {}: activities={}, health={}, sleep={}, weights={}, weather={}",
                status, userId, counts.activities, counts.health, counts.sleep, counts.weights, counts.weather);
        return status.name();
    }

    /** 청크 저장. 날씨 보강은 저장 커밋 이후 트랜잭션 밖에서 수행. */
    private boolean persistChunk(Long userId, DateRange chunk, JsonNode data, Counts counts, List<String> issues) {
        List<GarminActivityPersister.WeatherCandidate> weatherCandidates = List.of();
        boolean ok = true;
        try {
            ChunkCounts c = transactionTemplate.execute(status -> {
                GarminActivityPersister.Result activities = activityPersister.save(userId, data.get("activities"));
                int health = dailyPersister.saveHealthMetrics(userId, data.get("health"));
                int sleep = dailyPersister.saveSleepSessions(userId, data.get("sleep"));
                dailyPersister.saveDailyExtras(userId, data.get("extras"));
                return new ChunkCounts(activities, health, sleep);
            });
            if (c != null) {
                counts.activities += c.activities().saved();
                counts.health += c.health();
                counts.sleep += c.sleep();
                weatherCandidates = c.activities().weatherCandidates();
            }
        } catch (Exception e) {
            ok = false;
            log.error("Saving chunk {} failed for user {}", chunk, userId, e);
            issues.add(chunk + ": save failed: " + e.getMessage());
        }

        // 체중은 실패해도 나머지 저장에 영향이 없도록 별도 트랜잭션
        try {
            Integer weights = transactionTemplate.execute(status -> dailyPersister.saveWeights(userId, data.get("weights")));
            counts.weights += weights != null ? weights : 0;
        } catch (Exception e) {
            log.warn("Weight sync failed for chunk {}: {}", chunk, e.getMessage());
            issues.add(chunk + ": weights save failed");
        }

        counts.weather += weatherEnricher.enrich(weatherCandidates);
        return ok;
    }

    private record ChunkCounts(GarminActivityPersister.Result activities, int health, int sleep) {}

    static String summarizeEndpointErrors(List<GarminPythonClient.EndpointError> errors) {
        Map<String, Long> grouped = errors.stream()
                .collect(Collectors.groupingBy(e -> e.endpoint() + " (" + e.kind() + ")",
                        LinkedHashMap::new, Collectors.counting()));
        String summary = grouped.entrySet().stream()
                .map(e -> e.getKey() + " x" + e.getValue())
                .collect(Collectors.joining(", "));
        return errors.size() + " endpoint error(s): " + summary + "; first: " + errors.get(0).message();
    }

    private static String join(String head, List<String> issues) {
        StringBuilder sb = new StringBuilder();
        if (head != null) sb.append(head);
        for (String issue : issues) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(issue);
        }
        String s = sb.toString();
        return s.length() > MAX_ERROR_MESSAGE_LENGTH ? s.substring(0, MAX_ERROR_MESSAGE_LENGTH - 1) + "…" : s;
    }

    private String extractEmail(ProviderConnection conn) {
        Map<String, Object> auth = conn.getAuthPayload();
        if (auth != null && auth.get("email") != null) {
            return auth.get("email").toString();
        }
        throw new IllegalStateException("Garmin email not found");
    }

    private String extractPassword(ProviderConnection conn) {
        Map<String, Object> auth = conn.getAuthPayload();
        if (auth != null && auth.get("password") != null) {
            return secretCrypto.decrypt(auth.get("password").toString());
        }
        throw new IllegalStateException("Garmin password not found");
    }

    private void markFinished(SyncLog syncLog, SyncStatus status, Counts counts, String error) {
        syncLog.setStatus(status.name());
        syncLog.setActivitiesCount(counts.activities);
        syncLog.setHealthMetricsCount(counts.health);
        syncLog.setSleepCount(counts.sleep);
        syncLog.setWeightsCount(counts.weights);
        syncLog.setErrorMessage(error != null && error.length() > MAX_ERROR_MESSAGE_LENGTH
                ? error.substring(0, MAX_ERROR_MESSAGE_LENGTH) : error);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private void updateConnectionAfterSync(ProviderConnection conn, LocalDate lastSyncDate) {
        try {
            conn.setLastSyncedAt(Instant.now());
            Map<String, Object> config = conn.getSyncConfig() != null ? new HashMap<>(conn.getSyncConfig()) : new HashMap<>();
            config.put("last_sync_date", lastSyncDate.toString());
            conn.setSyncConfig(config);
            providerRepo.save(conn);
        } catch (Exception e) {
            log.warn("Failed to update connection after sync: {}", e.getMessage());
        }
    }

    private void publishFinished(Long userId, Long syncLogId, String status, boolean scheduled) {
        try {
            eventPublisher.publishEvent(new GarminSyncFinishedEvent(userId, syncLogId, status, scheduled));
        } catch (Exception e) {
            log.warn("Sync finished listener failed for user {}: {}", userId, e.getMessage());
        }
    }

    static List<DateRange> splitIntoChunks(LocalDate from, LocalDate to, int days) {
        int size = Math.max(1, days);
        List<DateRange> chunks = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            LocalDate end = current.plusDays(size - 1L);
            if (end.isAfter(to)) end = to;
            chunks.add(new DateRange(current, end));
            current = end.plusDays(1);
        }
        return chunks;
    }

    record DateRange(LocalDate from, LocalDate to) {
        @Override
        public String toString() {
            return from + "~" + to;
        }
    }

    static final class Counts {
        int activities;
        int health;
        int sleep;
        int weights;
        int weather;

        int total() {
            return activities + health + sleep + weights;
        }
    }
}
