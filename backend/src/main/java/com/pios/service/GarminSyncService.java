package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pios.domain.*;
import com.pios.domain.enums.SyncStatus;
import com.pios.domain.enums.SyncType;
import com.pios.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarminSyncService {

    private final ProviderConnectionRepository providerRepo;
    private final SyncLogRepository syncLogRepo;
    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final GarminActivityLapRepository lapRepo;
    private final GarminPythonClient pythonClient;
    private final GraphProjectorService graphProjector;
    private final WeatherService weatherService;

    @Value("${sync.rate-limit-seconds:30}")
    private int rateLimitSeconds;

    @Value("${sync.chunk-days:30}")
    private int chunkDays;

    @Value("${sync.default-full-sync-months:12}")
    private int defaultFullSyncMonths;

    /**
     * Rate limit 체크 후 동기화 실행
     */
    @Transactional
    public void sync(Long userId, SyncType syncType, LocalDate fromDate, LocalDate toDate) {
        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .orElseThrow(() -> new IllegalArgumentException("Garmin not connected"));

        // Rate limit 체크
        checkRateLimit(userId);

        // 동기화 기간 결정
        if (fromDate == null || toDate == null) {
            LocalDate today = LocalDate.now();
            if (syncType == SyncType.FULL) {
                fromDate = today.minusMonths(defaultFullSyncMonths);
                toDate = today;
            } else {
                int rangeDays = getSyncRangeDays(conn);
                fromDate = today.minusDays(rangeDays);
                toDate = today;
            }
        }

        // sync_log 생성
        SyncLog syncLog = SyncLog.builder()
                .user(User.builder().id(userId).build())
                .providerType("GARMIN")
                .syncType(syncType.name())
                .status(SyncStatus.RUNNING.name())
                .dateFrom(fromDate)
                .dateTo(toDate)
                .startedAt(Instant.now())
                .build();
        syncLog = syncLogRepo.save(syncLog);

        // 백그라운드에서 실행 (FULL 동기화는 오래 걸릴 수 있음)
        if (syncType == SyncType.FULL) {
            runFullSyncAsync(userId, conn, syncLog, fromDate, toDate);
        } else {
            runIncrementalSync(userId, conn, syncLog, fromDate, toDate);
        }
    }

    @Async("syncTaskExecutor")
    public void runFullSyncAsync(Long userId, ProviderConnection conn, SyncLog syncLog,
                                  LocalDate fromDate, LocalDate toDate) {
        try {
            executeSync(userId, conn, syncLog, fromDate, toDate);
        } catch (Exception e) {
            log.error("Full sync failed for user {}", userId, e);
            markFailed(syncLog, e.getMessage());
        }
    }

    @Transactional
    public void runIncrementalSync(Long userId, ProviderConnection conn, SyncLog syncLog,
                                    LocalDate fromDate, LocalDate toDate) {
        try {
            executeSync(userId, conn, syncLog, fromDate, toDate);
        } catch (Exception e) {
            log.error("Incremental sync failed for user {}", userId, e);
            markFailed(syncLog, e.getMessage());
            throw e;
        }
    }

    private void executeSync(Long userId, ProviderConnection conn, SyncLog syncLog,
                             LocalDate fromDate, LocalDate toDate) {
        String email = extractEmail(conn);
        String password = extractPassword(conn);

        int totalActivities = 0;
        int totalHealth = 0;
        int totalSleep = 0;

        // 청크 단위 처리
        List<DateRange> chunks = splitIntoChunks(fromDate, toDate, chunkDays);
        for (DateRange chunk : chunks) {
            log.info("Syncing chunk: {} to {} for user {}", chunk.from, chunk.to, userId);

            // Activities
            try {
                GarminPythonClient.SyncResult result = pythonClient.fetch(
                        email, password, chunk.from, chunk.to, GarminPythonClient.DataType.ALL);
                JsonNode data = result.data();

                totalActivities += saveActivities(userId, data.get("activities"));
                totalHealth += saveHealthMetrics(userId, data.get("health"));
                totalSleep += saveSleepSessions(userId, data.get("sleep"));
            } catch (Exception e) {
                log.error("Chunk sync failed: {} to {}", chunk.from, chunk.to, e);
                markPartial(syncLog, totalActivities, totalHealth, totalSleep, e.getMessage());
                return;
            }
        }

        // 완료 처리
        markCompleted(syncLog, totalActivities, totalHealth, totalSleep);
        updateConnectionAfterSync(conn, toDate);

        // 그래프 투영
        try {
            graphProjector.projectUserData(userId);
        } catch (Exception e) {
            log.error("Graph projection failed for user {}", userId, e);
        }
    }

    private int saveActivities(Long userId, JsonNode activitiesNode) {
        if (activitiesNode == null || !activitiesNode.isArray()) return 0;
        int count = 0;
        User user = User.builder().id(userId).build();

        for (JsonNode node : activitiesNode) {
            String garminId = node.get("garmin_activity_id").asText();
            if (garminId == null || garminId.isEmpty()) continue;

            Optional<Activity> existing = activityRepo.findByUserIdAndExternalActivityId(userId, garminId);
            Activity activity = existing.orElseGet(() -> Activity.builder()
                    .user(user)
                    .externalActivityId(garminId)
                    .sourceType("GARMIN")
                    .build());

            activity.setActivityType(getText(node, "activity_type"));
            activity.setActivityName(getText(node, "activity_name"));
            activity.setStartTime(parseLocalDateTime(getText(node, "start_time")));
            activity.setDurationSeconds(getInt(node, "duration_seconds"));
            activity.setDistanceMeters(getDecimal(node, "distance_meters"));
            activity.setAveragePaceSeconds(getDecimal(node, "average_pace_seconds"));
            activity.setAverageHeartRate(getInt(node, "average_heart_rate"));
            activity.setMaxHeartRate(getInt(node, "max_heart_rate"));
            activity.setCalories(getInt(node, "calories"));
            activity.setElevationGainMeters(getDecimal(node, "elevation_gain_meters"));
            activity.setRawPayload(jsonNodeToMap(node.get("raw_payload")));

            Activity savedActivity = activityRepo.save(activity);
            count++;

            // 랩 저장
            saveActivityLaps(savedActivity, node.get("laps"));

            // 러닝 날씨 저장
            fetchAndSaveWeather(savedActivity, node.get("raw_payload"));
        }
        return count;
    }

    private void fetchAndSaveWeather(Activity activity, JsonNode rawPayload) {
        if (activity.getStartTime() == null) return;
        String type = activity.getActivityType();
        if (type == null) return;
        String t = type.toLowerCase();
        boolean isRunning = t.equals("running") || t.contains("run") || t.contains("treadmill") || t.contains("track");
        if (!isRunning) return;

        JsonNode startLat = rawPayload != null ? rawPayload.get("startLatitude") : null;
        JsonNode startLon = rawPayload != null ? rawPayload.get("startLongitude") : null;
        if (startLat == null || startLat.isNull() || startLon == null || startLon.isNull()) {
            return;
        }

        try {
            double lat = startLat.asDouble();
            double lon = startLon.asDouble();
            if (lat == 0.0 && lon == 0.0) return;

            WeatherService.WeatherData weather = weatherService.fetchWeather(lat, lon, activity.getStartTime());
            if (weather != null) {
                activity.setWeatherTemperature(weather.getTemperature());
                activity.setWeatherHumidity(weather.getHumidity());
                activity.setWeatherWindSpeed(weather.getWindSpeed());
                activity.setWeatherCondition(weather.getCondition());
                activity.setWeatherRaw(weather.getRaw());
                activityRepo.save(activity);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch weather for activity {}: {}", activity.getId(), e.getMessage());
        }
    }

    private void saveActivityLaps(Activity activity, JsonNode lapsNode) {
        if (lapsNode == null || !lapsNode.isArray()) return;

        // 기존 랩 삭제 후 재삽입 (랩 데이터는 불변으로 가정)
        lapRepo.deleteByActivityId(activity.getId());
        lapRepo.flush(); // DELETE 즉시 실행 (INSERT보다 먼저 실행되어야 함)

        List<GarminActivityLap> laps = new ArrayList<>();
        for (JsonNode lapNode : lapsNode) {
            GarminActivityLap lap = GarminActivityLap.builder()
                    .activity(activity)
                    .lapIndex(getInt(lapNode, "lap_index"))
                    .startTime(parseInstant(getText(lapNode, "start_time")))
                    .durationSeconds(getInt(lapNode, "duration_seconds"))
                    .distanceMeters(getDecimal(lapNode, "distance_meters"))
                    .averagePaceSeconds(getDecimal(lapNode, "average_pace_seconds"))
                    .averageHeartRate(getInt(lapNode, "average_heart_rate"))
                    .maxHeartRate(getInt(lapNode, "max_heart_rate"))
                    .rawPayload(jsonNodeToMap(lapNode.get("raw_payload")))
                    .build();
            laps.add(lap);
        }
        if (!laps.isEmpty()) {
            lapRepo.saveAll(laps);
            lapRepo.flush(); // INSERT 즉시 실행
        }
    }

    private int saveHealthMetrics(Long userId, JsonNode healthNode) {
        if (healthNode == null || !healthNode.isArray()) return 0;
        int count = 0;
        User user = User.builder().id(userId).build();

        for (JsonNode node : healthNode) {
            LocalDate date = parseDate(getText(node, "metric_date"));
            if (date == null) continue;

            Optional<GarminDailyHealthMetric> existing = healthRepo.findByUserIdAndMetricDate(userId, date);
            GarminDailyHealthMetric metric = existing.orElseGet(() -> GarminDailyHealthMetric.builder()
                    .user(user)
                    .metricDate(date)
                    .build());

            metric.setRestingHeartRate(getInt(node, "resting_heart_rate"));
            metric.setHrvAvg(getDecimal(node, "hrv_avg"));
            metric.setStressAvg(getDecimal(node, "stress_avg"));
            metric.setBodyBatteryMin(getInt(node, "body_battery_min"));
            metric.setBodyBatteryMax(getInt(node, "body_battery_max"));
            metric.setSteps(getInt(node, "steps"));
            metric.setCaloriesTotal(getInt(node, "calories_total"));
            metric.setRawPayload(jsonNodeToMap(node.get("raw_payload")));

            healthRepo.save(metric);
            count++;
        }
        return count;
    }

    private int saveSleepSessions(Long userId, JsonNode sleepNode) {
        if (sleepNode == null || !sleepNode.isArray()) return 0;
        int count = 0;
        User user = User.builder().id(userId).build();

        for (JsonNode node : sleepNode) {
            LocalDate date = parseDate(getText(node, "sleep_date"));
            if (date == null) continue;

            Optional<GarminSleepSession> existing = sleepRepo.findByUserIdAndSleepDate(userId, date);
            GarminSleepSession sleep = existing.orElseGet(() -> GarminSleepSession.builder()
                    .user(user)
                    .sleepDate(date)
                    .build());

            sleep.setStartTime(parseInstant(getText(node, "start_time")));
            sleep.setEndTime(parseInstant(getText(node, "end_time")));
            sleep.setTotalSleepSeconds(getInt(node, "total_sleep_seconds"));
            sleep.setDeepSleepSeconds(getInt(node, "deep_sleep_seconds"));
            sleep.setLightSleepSeconds(getInt(node, "light_sleep_seconds"));
            sleep.setRemSleepSeconds(getInt(node, "rem_sleep_seconds"));
            sleep.setAwakeSeconds(getInt(node, "awake_seconds"));
            sleep.setSleepScore(getInt(node, "sleep_score"));
            sleep.setRawPayload(jsonNodeToMap(node.get("raw_payload")));

            sleepRepo.save(sleep);
            count++;
        }
        return count;
    }

    // --- Helper methods ---

    private void checkRateLimit(Long userId) {
        Optional<SyncLog> latest = syncLogRepo.findLatestByUserIdAndProviderType(userId, "GARMIN");
        if (latest.isPresent()) {
            SyncLog log = latest.get();
            if (SyncStatus.RUNNING.name().equals(log.getStatus())) {
                throw new TooFrequentSyncException("Sync is already running");
            }
            Instant lastStarted = log.getStartedAt();
            if (lastStarted != null &&
                Duration.between(lastStarted, Instant.now()).getSeconds() < rateLimitSeconds) {
                throw new TooFrequentSyncException(
                        "Please wait " + rateLimitSeconds + " seconds between syncs");
            }
        }
    }

    private int getSyncRangeDays(ProviderConnection conn) {
        Map<String, Object> config = conn.getSyncConfig();
        if (config != null && config.get("sync_range_days") instanceof Number n) {
            return n.intValue();
        }
        return 7;
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
            return auth.get("password").toString();
        }
        throw new IllegalStateException("Garmin password not found");
    }

    private void markCompleted(SyncLog syncLog, int activities, int health, int sleep) {
        syncLog.setStatus(SyncStatus.COMPLETED.name());
        syncLog.setActivitiesCount(activities);
        syncLog.setHealthMetricsCount(health);
        syncLog.setSleepCount(sleep);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private void markFailed(SyncLog syncLog, String error) {
        syncLog.setStatus(SyncStatus.FAILED.name());
        syncLog.setErrorMessage(error);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private void markPartial(SyncLog syncLog, int activities, int health, int sleep, String error) {
        syncLog.setStatus(SyncStatus.PARTIAL.name());
        syncLog.setActivitiesCount(activities);
        syncLog.setHealthMetricsCount(health);
        syncLog.setSleepCount(sleep);
        syncLog.setErrorMessage(error);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private void updateConnectionAfterSync(ProviderConnection conn, LocalDate lastSyncDate) {
        conn.setLastSyncedAt(Instant.now());
        Map<String, Object> config = conn.getSyncConfig();
        if (config == null) config = new HashMap<>();
        config.put("last_sync_date", lastSyncDate.toString());
        conn.setSyncConfig(config);
        providerRepo.save(conn);
    }

    private List<DateRange> splitIntoChunks(LocalDate from, LocalDate to, int days) {
        List<DateRange> chunks = new ArrayList<>();
        LocalDate current = from;
        while (current.isBefore(to) || current.isEqual(to)) {
            LocalDate end = current.plusDays(days - 1);
            if (end.isAfter(to)) end = to;
            chunks.add(new DateRange(current, end));
            current = end.plusDays(1);
        }
        return chunks;
    }

    private record DateRange(LocalDate from, LocalDate to) {}

    // --- JSON helpers ---

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private Integer getInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return value.isNumber() ? value.asInt() : null;
    }

    private BigDecimal getDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return value.isNumber() ? BigDecimal.valueOf(value.asDouble()) : null;
    }

    private LocalDateTime parseLocalDateTime(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            // Garmin local format: "2026-05-02 09:14:31" (space-separated)
            String normalized = text.replace(" ", "T");
            if (normalized.endsWith(".0")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            // Garmin format: "2024-01-15T08:30:00.0"
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (Exception e) {
            try {
                // Garmin format: "2026-05-02 09:14:31" (space-separated)
                return LocalDateTime.parse(text.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            } catch (Exception e2) {
                try {
                    return Instant.parse(text);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) return new HashMap<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(node, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static class TooFrequentSyncException extends RuntimeException {
        public TooFrequentSyncException(String message) {
            super(message);
        }
    }
}
