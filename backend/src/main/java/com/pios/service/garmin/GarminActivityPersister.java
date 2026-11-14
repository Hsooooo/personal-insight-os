package com.pios.service.garmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.pios.domain.Activity;
import com.pios.domain.GarminActivityLap;
import com.pios.domain.User;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminActivityLapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 활동 + 랩 저장. 기존 활동은 한 번의 IN 조회로 가져오고, 랩은 활동 단위로 교체한다.
 * 호출 측 트랜잭션 안에서 실행된다.
 */
@Component
@RequiredArgsConstructor
public class GarminActivityPersister {

    private final ActivityRepository activityRepo;
    private final GarminActivityLapRepository lapRepo;
    private final GarminJson json;

    /** 날씨 조회 대상 (저장 이후 트랜잭션 밖에서 조회) */
    public record WeatherCandidate(Long activityId, double latitude, double longitude, LocalDateTime startTime) {}

    public record Result(int saved, List<WeatherCandidate> weatherCandidates) {}

    public Result save(Long userId, JsonNode activitiesNode) {
        if (activitiesNode == null || !activitiesNode.isArray() || activitiesNode.isEmpty()) {
            return new Result(0, List.of());
        }
        Map<String, JsonNode> byGarminId = new LinkedHashMap<>();
        for (JsonNode node : activitiesNode) {
            String garminId = json.text(node, "garmin_activity_id");
            if (garminId != null) {
                byGarminId.put(garminId, node);
            }
        }
        if (byGarminId.isEmpty()) {
            return new Result(0, List.of());
        }

        Map<String, Activity> existing = activityRepo
                .findByUserIdAndExternalActivityIdIn(userId, byGarminId.keySet()).stream()
                .collect(Collectors.toMap(Activity::getExternalActivityId, Function.identity(), (a, b) -> a));
        User user = User.builder().id(userId).build();

        List<Activity> toSave = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : byGarminId.entrySet()) {
            JsonNode node = e.getValue();
            Activity activity = existing.getOrDefault(e.getKey(), null);
            if (activity == null) {
                activity = Activity.builder()
                        .user(user)
                        .externalActivityId(e.getKey())
                        .sourceType("GARMIN")
                        .build();
            }
            activity.setActivityType(json.text(node, "activity_type"));
            activity.setActivityName(json.text(node, "activity_name"));
            activity.setStartTime(json.localDateTime(json.text(node, "start_time")));
            activity.setDurationSeconds(json.integer(node, "duration_seconds"));
            activity.setDistanceMeters(json.decimal(node, "distance_meters"));
            activity.setAveragePaceSeconds(json.decimal(node, "average_pace_seconds"));
            activity.setAverageHeartRate(json.positiveInteger(node, "average_heart_rate"));
            activity.setMaxHeartRate(json.positiveInteger(node, "max_heart_rate"));
            activity.setCalories(json.integer(node, "calories"));
            activity.setElevationGainMeters(json.decimal(node, "elevation_gain_meters"));
            activity.setRawPayload(json.toMap(node.get("raw_payload")));
            toSave.add(activity);
        }
        List<Activity> saved = activityRepo.saveAll(toSave);

        // 랩 교체: 랩 정보가 온 활동만 (일괄 삭제 후 일괄 삽입)
        Map<Long, JsonNode> lapsByActivity = new LinkedHashMap<>();
        for (Activity a : saved) {
            JsonNode laps = byGarminId.get(a.getExternalActivityId()).get("laps");
            if (laps != null && laps.isArray()) {
                lapsByActivity.put(a.getId(), laps);
            }
        }
        if (!lapsByActivity.isEmpty()) {
            lapRepo.deleteByActivityIdIn(lapsByActivity.keySet());
            List<GarminActivityLap> laps = new ArrayList<>();
            Map<Long, Activity> savedById = saved.stream()
                    .collect(Collectors.toMap(Activity::getId, Function.identity()));
            lapsByActivity.forEach((activityId, lapNodes) -> {
                Set<Integer> seenIndexes = new HashSet<>();
                for (JsonNode lapNode : lapNodes) {
                    Integer lapIndex = json.integer(lapNode, "lap_index");
                    if (lapIndex != null && !seenIndexes.add(lapIndex)) continue;
                    laps.add(GarminActivityLap.builder()
                            .activity(savedById.get(activityId))
                            .lapIndex(lapIndex)
                            .startTime(json.instant(json.text(lapNode, "start_time")))
                            .durationSeconds(json.integer(lapNode, "duration_seconds"))
                            .distanceMeters(json.decimal(lapNode, "distance_meters"))
                            .averagePaceSeconds(json.decimal(lapNode, "average_pace_seconds"))
                            .averageHeartRate(json.positiveInteger(lapNode, "average_heart_rate"))
                            .maxHeartRate(json.positiveInteger(lapNode, "max_heart_rate"))
                            .rawPayload(json.toMap(lapNode.get("raw_payload")))
                            .build());
                }
            });
            if (!laps.isEmpty()) {
                lapRepo.saveAll(laps);
            }
        }

        List<WeatherCandidate> candidates = new ArrayList<>();
        for (Activity a : saved) {
            weatherCandidate(a).ifPresent(candidates::add);
        }
        return new Result(saved.size(), candidates);
    }

    /** 러닝 계열이고 좌표가 있으며 아직 날씨가 없는 활동만 */
    Optional<WeatherCandidate> weatherCandidate(Activity a) {
        if (a.getStartTime() == null || a.getActivityType() == null) return Optional.empty();
        if (a.getWeatherCondition() != null || a.getWeatherTemperature() != null) return Optional.empty();
        String t = a.getActivityType().toLowerCase(Locale.ROOT);
        boolean running = t.contains("run") || t.contains("treadmill") || t.contains("track");
        if (!running) return Optional.empty();
        Map<String, Object> raw = a.getRawPayload();
        if (raw == null) return Optional.empty();
        Double lat = asDouble(raw.get("startLatitude"));
        Double lon = asDouble(raw.get("startLongitude"));
        if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) return Optional.empty();
        return Optional.of(new WeatherCandidate(a.getId(), lat, lon, a.getStartTime()));
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
