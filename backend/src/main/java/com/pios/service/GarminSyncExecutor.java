package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pios.domain.*;
import com.pios.domain.enums.SyncStatus;
import com.pios.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GarminSyncExecutor {

    private final ProviderConnectionRepository providerRepo;
    private final SyncLogRepository syncLogRepo;
    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final GarminActivityLapRepository lapRepo;
    private final GarminPythonClient pythonClient;
    private final GraphProjectorService graphProjector;
    private final WeatherService weatherService;

    @Value("${sync.chunk-days:30}")
    private int chunkDays;

    @Async("syncTaskExecutor")
    @Transactional
    public void runSyncAsync(Long userId, Long syncLogId, LocalDate fromDate, LocalDate toDate) {
        SyncLog syncLog = syncLogRepo.findById(syncLogId)
                .orElseThrow(() -> new IllegalStateException("Sync log not found: " + syncLogId));
        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .orElseThrow(() -> new IllegalStateException("Garmin not connected"));

        try {
            executeSync(userId, conn, syncLog, fromDate, toDate);
        } catch (Exception e) {
            log.error("Sync failed for user {}", userId, e);
            markFailed(syncLog, e.getMessage());
        }
    }

    private void executeSync(Long userId, ProviderConnection conn, SyncLog syncLog,
                             LocalDate fromDate, LocalDate toDate) {
        String email = extractEmail(conn);
        String password = extractPassword(conn);

        int totalActivities = 0;
        int totalHealth = 0;
        int totalSleep = 0;
        int totalWeights = 0;

        List<DateRange> chunks = splitIntoChunks(fromDate, toDate, chunkDays);
        for (DateRange chunk : chunks) {
            log.info("Syncing chunk: {} to {} for user {}", chunk.from, chunk.to, userId);

            try {
                GarminPythonClient.SyncResult result = pythonClient.fetch(
                        email, password, chunk.from, chunk.to, GarminPythonClient.DataType.ALL);
                JsonNode data = result.data();

                try {
                    totalWeights += saveWeights(userId, data.get("weights"));
                } catch (Exception e) {
                    log.warn("Weight sync failed for chunk {} to {}: {}", chunk.from, chunk.to, e.getMessage());
                }
                totalActivities += saveActivities(userId, data.get("activities"));
                totalHealth += saveHealthMetrics(userId, data.get("health"));
                totalSleep += saveSleepSessions(userId, data.get("sleep"));
            } catch (Exception e) {
                log.error("Chunk sync failed: {} to {}", chunk.from, chunk.to, e);
                markPartial(syncLog, totalActivities, totalHealth, totalSleep, totalWeights, e.getMessage());
                return;
            }
        }

        markCompleted(syncLog, totalActivities, totalHealth, totalSleep, totalWeights);
        updateConnectionAfterSync(conn, toDate);

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

            saveActivityLaps(savedActivity, node.get("laps"));
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

        lapRepo.deleteByActivityId(activity.getId());
        lapRepo.flush();

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
            lapRepo.flush();
        }
    }

    private int saveWeights(Long userId, JsonNode weightsNode) {
        if (weightsNode == null || !weightsNode.isArray()) return 0;
        int count = 0;
        User user = User.builder().id(userId).build();

        for (JsonNode node : weightsNode) {
            LocalDate date = parseDate(getText(node, "metric_date"));
            if (date == null) continue;

            Optional<GarminDailyHealthMetric> existing = healthRepo.findByUserIdAndMetricDate(userId, date);
            GarminDailyHealthMetric metric = existing.orElseGet(() -> GarminDailyHealthMetric.builder()
                    .user(user)
                    .metricDate(date)
                    .build());

            metric.setWeightKg(getDecimal(node, "weight_kg"));
            metric.setRawPayload(jsonNodeToMap(node.get("raw_payload")));

            healthRepo.save(metric);
            count++;
        }
        return count;
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

    private void markCompleted(SyncLog syncLog, int activities, int health, int sleep, int weights) {
        syncLog.setStatus(SyncStatus.COMPLETED.name());
        syncLog.setActivitiesCount(activities);
        syncLog.setHealthMetricsCount(health);
        syncLog.setSleepCount(sleep);
        syncLog.setWeightsCount(weights);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private void markFailed(SyncLog syncLog, String error) {
        syncLog.setStatus(SyncStatus.FAILED.name());
        syncLog.setErrorMessage(error);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private void markPartial(SyncLog syncLog, int activities, int health, int sleep, int weights, String error) {
        syncLog.setStatus(SyncStatus.PARTIAL.name());
        syncLog.setActivitiesCount(activities);
        syncLog.setHealthMetricsCount(health);
        syncLog.setSleepCount(sleep);
        syncLog.setWeightsCount(weights);
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
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (Exception e) {
            try {
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
}
