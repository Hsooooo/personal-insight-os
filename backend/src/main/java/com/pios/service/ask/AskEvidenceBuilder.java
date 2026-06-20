package com.pios.service.ask;

import com.pios.domain.Activity;
import com.pios.dto.AskEvidence;
import com.pios.dto.AskPeriod;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@UtilityClass
public class AskEvidenceBuilder {

    private static final Map<AskIntent, List<String>> PRIORITY_METRICS = Map.of(
            AskIntent.CONDITION, List.of("hrvAvg", "restingHeartRate", "stressAvg", "bodyBattery"),
            AskIntent.SLEEP, List.of("totalSleepSeconds", "sleepScore", "deepSleepSeconds", "remSleepSeconds"),
            AskIntent.TRAINING, List.of("activityCount", "totalDurationSeconds", "trainingDays", "averageHeartRate"),
            AskIntent.PERFORMANCE, List.of("activityCount", "totalDistanceMeters", "averageHeartRate", "totalDurationSeconds"),
            AskIntent.WORKOUT_SUMMARY, List.of("activityCount", "totalDurationSeconds", "totalDistanceMeters"),
            AskIntent.GENERAL, List.of("hrvAvg", "totalSleepSeconds", "sleepScore", "activityCount", "stressAvg")
    );

    public static List<AskEvidence> buildFromStatistics(EvidenceStatistics statistics, AskIntent intent, AskPeriod period) {
        List<AskEvidence> evidences = new ArrayList<>();
        List<String> priority = PRIORITY_METRICS.getOrDefault(intent, PRIORITY_METRICS.get(AskIntent.GENERAL));

        Stream.concat(
                Stream.concat(statistics.getHealthMetrics().stream(), statistics.getSleepMetrics().stream()),
                statistics.getActivityMetrics().stream()
        ).forEach(metric -> {
            if (!priority.contains(metric.getMetric())) return;
            if (metric.getCurrentValue() == null) return;

            String observation = String.format("%s %s%s",
                    metric.getLabel(), formatValue(metric.getCurrentValue()), metric.getUnit());

            String comparison = "기준선 데이터 없음";
            if (metric.getBaselineValue() != null) {
                if (metric.getChangeRate() == null) {
                    comparison = String.format("기준선(%s%s)과 동일", formatValue(metric.getBaselineValue()), metric.getUnit());
                } else {
                    String direction = metric.getChangeRate().compareTo(BigDecimal.ZERO) > 0 ? "높음" : "낮음";
                    comparison = String.format("기준선(%s%s)보다 %s%% %s",
                            formatValue(metric.getBaselineValue()),
                            metric.getUnit(),
                            formatValue(metric.getChangeRate().abs()),
                            direction);
                }
            }

            String route = metricRoute(metric);

            evidences.add(AskEvidence.builder()
                    .type(metricType(metric.getMetric()))
                    .label(metric.getLabel())
                    .observation(observation)
                    .comparison(comparison)
                    .currentValue(metric.getCurrentValue())
                    .baselineValue(metric.getBaselineValue())
                    .changeRate(metric.getChangeRate())
                    .unit(metric.getUnit())
                    .sourceId(metric.getSourceId())
                    .sourceDate(metric.getSourceDate())
                    .route(route)
                    .build());
        });

        return evidences;
    }

    public static List<AskEvidence> buildFromActivities(List<Activity> activities, AskPeriod period) {
        return activities.stream()
                .map(a -> {
                    String date = a.getStartTime() != null ? a.getStartTime().toLocalDate().toString() : "";
                    double km = a.getDistanceMeters() != null ? a.getDistanceMeters().doubleValue() / 1000.0 : 0.0;
                    String dur = formatDuration(a.getDurationSeconds());
                    return AskEvidence.builder()
                            .type("ACTIVITY")
                            .label(a.getActivityName() != null ? a.getActivityName() : "활동")
                            .observation(String.format("%s (%s) / %s / %.2fkm", date, a.getActivityType(), dur, km))
                            .comparison(averageHeartRateComparison(a))
                            .sourceId(a.getId())
                            .sourceDate(date)
                            .route("/activities/" + a.getId())
                            .build();
                })
                .toList();
    }

    private static String metricType(String metric) {
        return switch (metric) {
            case "restingHeartRate", "hrvAvg", "stressAvg", "bodyBattery" -> "HEALTH_METRIC";
            case "totalSleepSeconds", "sleepScore", "deepSleepSeconds", "remSleepSeconds" -> "SLEEP";
            default -> "ACTIVITY";
        };
    }

    private static String metricRoute(MetricStatistic metric) {
        if ("ACTIVITY".equals(metricType(metric.getMetric()))) {
            return metric.getSourceId() != null ? "/activities/" + metric.getSourceId() : null;
        }
        return metric.getSourceDate() != null ? "/health?date=" + metric.getSourceDate() : null;
    }

    private static String averageHeartRateComparison(Activity a) {
        if (a.getAverageHeartRate() == null) return "심박 데이터 없음";
        return String.format("평균 심박 %d bpm", a.getAverageHeartRate());
    }

    private static String formatValue(BigDecimal value) {
        if (value == null) return "-";
        // 정수처럼 보이면 소수점 제거
        if (value.stripTrailingZeros().scale() <= 0) {
            return value.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return "-";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        if (h > 0) {
            return String.format("%d:%02d", h, m);
        }
        return String.format("%d분", m);
    }
}
