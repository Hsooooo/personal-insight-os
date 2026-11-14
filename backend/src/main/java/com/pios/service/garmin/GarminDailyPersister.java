package com.pios.service.garmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.pios.domain.GarminDailyHealthMetric;
import com.pios.domain.GarminDailyRaw;
import com.pios.domain.GarminSleepSession;
import com.pios.domain.User;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminDailyRawRepository;
import com.pios.repository.GarminSleepSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일 단위 데이터(건강 지표, 체중, 수면, 원문 extras) 저장. 청크 내 기존 행은 IN 조회 한 번으로 가져온다.
 * 호출 측 트랜잭션 안에서 실행된다.
 * <p>
 * 안정시 심박/HRV/수면 점수/총 수면 시간처럼 0이 불가능한 지표는 0이 와도 null로 저장해 기준선 평균을 오염시키지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GarminDailyPersister {

    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final GarminDailyRawRepository dailyRawRepo;
    private final GarminJson json;

    public int saveHealthMetrics(Long userId, JsonNode healthNode) {
        Map<LocalDate, JsonNode> byDate = byDate(healthNode, "metric_date");
        if (byDate.isEmpty()) return 0;
        Map<LocalDate, GarminDailyHealthMetric> existing = loadHealth(userId, byDate.keySet());
        User user = User.builder().id(userId).build();

        List<GarminDailyHealthMetric> toSave = new ArrayList<>();
        byDate.forEach((date, node) -> {
            GarminDailyHealthMetric metric = existing.computeIfAbsent(date,
                    d -> GarminDailyHealthMetric.builder().user(user).metricDate(d).build());
            metric.setRestingHeartRate(json.positiveInteger(node, "resting_heart_rate"));
            metric.setHrvAvg(json.positiveDecimal(node, "hrv_avg"));
            metric.setStressAvg(json.positiveDecimal(node, "stress_avg"));
            metric.setBodyBatteryMin(json.integer(node, "body_battery_min"));
            metric.setBodyBatteryMax(json.integer(node, "body_battery_max"));
            metric.setSteps(json.integer(node, "steps"));
            metric.setCaloriesTotal(json.integer(node, "calories_total"));
            metric.setAverageSpo2(json.positiveDecimal(node, "average_spo2"));
            metric.setLowestSpo2(json.positiveInteger(node, "lowest_spo2"));
            metric.setAvgWakingRespiration(json.positiveDecimal(node, "avg_waking_respiration"));
            metric.setFloorsAscended(json.decimal(node, "floors_ascended"));
            metric.setFloorsDescended(json.decimal(node, "floors_descended"));
            metric.setModerateIntensityMinutes(json.integer(node, "moderate_intensity_minutes"));
            metric.setVigorousIntensityMinutes(json.integer(node, "vigorous_intensity_minutes"));
            metric.setActiveKilocalories(json.decimal(node, "active_kilocalories"));
            metric.setBmrKilocalories(json.decimal(node, "bmr_kilocalories"));
            metric.setBodyBatteryAtWake(json.integer(node, "body_battery_at_wake"));
            metric.setBodyBatteryCharged(json.integer(node, "body_battery_charged"));
            metric.setBodyBatteryDrained(json.integer(node, "body_battery_drained"));
            metric.setTotalDistanceMeters(json.integer(node, "total_distance_meters"));
            metric.setRawPayload(json.toMap(node.get("raw_payload")));
            toSave.add(metric);
        });
        healthRepo.saveAll(toSave);
        return toSave.size();
    }

    /** 체중은 같은 날짜의 건강 지표 행에 병합된다 (health 저장 이후에 호출할 것). */
    public int saveWeights(Long userId, JsonNode weightsNode) {
        Map<LocalDate, JsonNode> byDate = byDate(weightsNode, "metric_date");
        if (byDate.isEmpty()) return 0;
        Map<LocalDate, GarminDailyHealthMetric> existing = loadHealth(userId, byDate.keySet());
        User user = User.builder().id(userId).build();

        List<GarminDailyHealthMetric> toSave = new ArrayList<>();
        byDate.forEach((date, node) -> {
            GarminDailyHealthMetric metric = existing.computeIfAbsent(date,
                    d -> GarminDailyHealthMetric.builder().user(user).metricDate(d).build());
            metric.setWeightKg(json.positiveDecimal(node, "weight_kg"));
            if (metric.getRawPayload() == null || metric.getRawPayload().isEmpty()) {
                // 건강 지표 원문이 없는 날만 체중 원문을 보관 (기존 stats 원문을 덮어쓰지 않음)
                metric.setRawPayload(json.toMap(node.get("raw_payload")));
            }
            toSave.add(metric);
        });
        healthRepo.saveAll(toSave);
        return toSave.size();
    }

    public int saveSleepSessions(Long userId, JsonNode sleepNode) {
        Map<LocalDate, JsonNode> byDate = byDate(sleepNode, "sleep_date");
        if (byDate.isEmpty()) return 0;
        Map<LocalDate, GarminSleepSession> existing = sleepRepo.findByUserIdAndSleepDateIn(userId, byDate.keySet())
                .stream().collect(Collectors.toMap(GarminSleepSession::getSleepDate, Function.identity(), (a, b) -> a));
        User user = User.builder().id(userId).build();

        List<GarminSleepSession> toSave = new ArrayList<>();
        byDate.forEach((date, node) -> {
            GarminSleepSession sleep = existing.computeIfAbsent(date,
                    d -> GarminSleepSession.builder().user(user).sleepDate(d).build());
            sleep.setStartTime(json.instant(json.text(node, "start_time")));
            sleep.setEndTime(json.instant(json.text(node, "end_time")));
            sleep.setTotalSleepSeconds(json.positiveInteger(node, "total_sleep_seconds"));
            sleep.setDeepSleepSeconds(json.integer(node, "deep_sleep_seconds"));
            sleep.setLightSleepSeconds(json.integer(node, "light_sleep_seconds"));
            sleep.setRemSleepSeconds(json.integer(node, "rem_sleep_seconds"));
            sleep.setAwakeSeconds(json.integer(node, "awake_seconds"));
            sleep.setSleepScore(json.positiveInteger(node, "sleep_score"));
            sleep.setNapSeconds(json.integer(node, "nap_seconds"));
            sleep.setAvgSleepStress(json.decimal(node, "avg_sleep_stress"));
            sleep.setSleepNeedMinutes(json.integer(node, "sleep_need_minutes"));
            sleep.setHrvStatus(json.text(node, "hrv_status"));
            sleep.setRawPayload(json.toMap(node.get("raw_payload")));
            toSave.add(sleep);
        });
        sleepRepo.saveAll(toSave);
        return toSave.size();
    }

    public int saveDailyExtras(Long userId, JsonNode extrasNode) {
        if (extrasNode == null || !extrasNode.isArray() || extrasNode.isEmpty()) return 0;
        record Key(LocalDate date, String type) {}
        Map<Key, JsonNode> byKey = new LinkedHashMap<>();
        for (JsonNode node : extrasNode) {
            LocalDate date = json.date(json.text(node, "metric_date"));
            String dataType = json.text(node, "data_type");
            if (date != null && dataType != null) {
                byKey.put(new Key(date, dataType), node);
            }
        }
        if (byKey.isEmpty()) return 0;
        Set<LocalDate> dates = byKey.keySet().stream().map(Key::date).collect(Collectors.toSet());
        Map<Key, GarminDailyRaw> existing = dailyRawRepo.findByUserIdAndMetricDateIn(userId, dates).stream()
                .collect(Collectors.toMap(r -> new Key(r.getMetricDate(), r.getDataType()), Function.identity(), (a, b) -> a));
        User user = User.builder().id(userId).build();

        List<GarminDailyRaw> toSave = new ArrayList<>();
        byKey.forEach((key, node) -> {
            GarminDailyRaw raw = existing.computeIfAbsent(key, k -> GarminDailyRaw.builder()
                    .user(user).metricDate(k.date()).dataType(k.type()).build());
            raw.setPayload(json.toPayloadMap(node.get("payload")));
            toSave.add(raw);
        });
        dailyRawRepo.saveAll(toSave);
        return toSave.size();
    }

    private Map<LocalDate, GarminDailyHealthMetric> loadHealth(Long userId, Set<LocalDate> dates) {
        return healthRepo.findByUserIdAndMetricDateIn(userId, dates).stream()
                .collect(Collectors.toMap(GarminDailyHealthMetric::getMetricDate, Function.identity(),
                        (a, b) -> a, HashMap::new));
    }

    private Map<LocalDate, JsonNode> byDate(JsonNode array, String dateField) {
        Map<LocalDate, JsonNode> byDate = new LinkedHashMap<>();
        if (array == null || !array.isArray()) return byDate;
        for (JsonNode node : array) {
            LocalDate date = json.date(json.text(node, dateField));
            if (date != null) {
                byDate.put(date, node);
            }
        }
        return byDate;
    }
}
