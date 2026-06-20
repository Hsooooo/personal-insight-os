package com.pios.service.ask;

import com.pios.dto.AskConfidence;
import com.pios.dto.AskPeriod;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@UtilityClass
public class ConfidenceScorer {

    private static final int SCALE = 2;
    private static final BigDecimal WEIGHT_COVERAGE = new BigDecimal("0.40");
    private static final BigDecimal WEIGHT_BASELINE = new BigDecimal("0.30");
    private static final BigDecimal WEIGHT_RELEVANCE = new BigDecimal("0.30");

    private static final Map<AskIntent, List<String>> RELEVANT_METRICS = Map.of(
            AskIntent.CONDITION, List.of("restingHeartRate", "hrvAvg", "stressAvg", "bodyBattery"),
            AskIntent.SLEEP, List.of("totalSleepSeconds", "sleepScore", "deepSleepSeconds", "remSleepSeconds"),
            AskIntent.TRAINING, List.of("activityCount", "totalDurationSeconds", "trainingDays", "averageHeartRate"),
            AskIntent.PERFORMANCE, List.of("activityCount", "totalDistanceMeters", "averageHeartRate", "totalDurationSeconds"),
            AskIntent.WORKOUT_SUMMARY, List.of("activityCount", "totalDurationSeconds", "totalDistanceMeters"),
            AskIntent.GENERAL, List.of("restingHeartRate", "hrvAvg", "stressAvg", "bodyBattery",
                    "totalSleepSeconds", "sleepScore", "activityCount")
    );

    public static AskConfidence score(AskPeriod period, EvidenceStatistics statistics, AskIntent intent) {
        long analysisDays = ChronoUnit.DAYS.between(period.getStart(), period.getEnd()) + 1;
        long baselineDays = ChronoUnit.DAYS.between(period.getBaselineStart(), period.getBaselineEnd()) + 1;

        List<MetricStatistic> allMetrics = Stream.of(
                statistics.getHealthMetrics().stream(),
                statistics.getSleepMetrics().stream(),
                statistics.getActivityMetrics().stream()
        ).flatMap(s -> s).toList();

        BigDecimal coverageScore = computeCoverageScore(allMetrics, analysisDays);
        BigDecimal baselineScore = computeBaselineScore(allMetrics, baselineDays);
        BigDecimal relevanceScore = computeRelevanceScore(statistics, intent);

        BigDecimal total = coverageScore.multiply(WEIGHT_COVERAGE)
                .add(baselineScore.multiply(WEIGHT_BASELINE))
                .add(relevanceScore.multiply(WEIGHT_RELEVANCE))
                .setScale(SCALE, RoundingMode.HALF_UP);

        List<String> reasons = new ArrayList<>();
        if (coverageScore.compareTo(new BigDecimal("0.1")) < 0) {
            reasons.add("분석 기간 내 데이터가 거의 없음");
        } else {
            reasons.add(String.format("분석 기간 데이터 %.0f%% 확보", coverageScore.multiply(new BigDecimal("100"))));
        }

        if (baselineScore.compareTo(new BigDecimal("0.5")) >= 0) {
            reasons.add("기준선 비교 가능");
        } else if (baselineScore.compareTo(new BigDecimal("0.1")) < 0) {
            reasons.add("기준선 데이터 부족");
        } else {
            reasons.add("기준선 데이터 일부 확보");
        }

        if (relevanceScore.compareTo(new BigDecimal("0.5")) >= 0) {
            reasons.add("질문과 관련된 지표 확보");
        } else {
            reasons.add("질문과 직접 관련된 지표 부족");
        }

        return AskConfidence.builder()
                .score(total)
                .level(level(total))
                .reasons(reasons)
                .build();
    }

    private static String level(BigDecimal score) {
        if (score.compareTo(new BigDecimal("0.80")) >= 0) return "HIGH";
        if (score.compareTo(new BigDecimal("0.50")) >= 0) return "MEDIUM";
        return "LOW";
    }

    private static BigDecimal computeCoverageScore(List<MetricStatistic> metrics, long analysisDays) {
        if (analysisDays <= 0) return BigDecimal.ZERO;
        List<BigDecimal> coverages = metrics.stream()
                .map(m -> BigDecimal.valueOf(m.getCurrentDays())
                        .divide(BigDecimal.valueOf(analysisDays), 4, RoundingMode.HALF_UP))
                .filter(bd -> bd.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (coverages.isEmpty()) return BigDecimal.ZERO;
        return coverages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(coverages.size()), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal computeBaselineScore(List<MetricStatistic> metrics, long baselineDays) {
        if (baselineDays <= 0) return BigDecimal.ZERO;
        List<BigDecimal> coverages = metrics.stream()
                .map(m -> BigDecimal.valueOf(m.getBaselineDays())
                        .divide(BigDecimal.valueOf(baselineDays), 4, RoundingMode.HALF_UP))
                .filter(bd -> bd.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (coverages.isEmpty()) return BigDecimal.ZERO;
        BigDecimal avg = coverages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(coverages.size()), SCALE, RoundingMode.HALF_UP);
        // Cap at 1.0
        return avg.min(BigDecimal.ONE);
    }

    private static BigDecimal computeRelevanceScore(EvidenceStatistics statistics, AskIntent intent) {
        List<String> expected = RELEVANT_METRICS.getOrDefault(intent, RELEVANT_METRICS.get(AskIntent.GENERAL));
        List<MetricStatistic> all = Stream.concat(
                Stream.concat(statistics.getHealthMetrics().stream(), statistics.getSleepMetrics().stream()),
                statistics.getActivityMetrics().stream()
        ).toList();

        long reliableCount = expected.stream()
                .filter(expectedMetric -> all.stream()
                        .anyMatch(m -> expectedMetric.equals(m.getMetric()) && m.isReliable()))
                .count();

        if (expected.isEmpty()) return BigDecimal.ZERO;
        return BigDecimal.valueOf(reliableCount)
                .divide(BigDecimal.valueOf(expected.size()), SCALE, RoundingMode.HALF_UP);
    }
}
