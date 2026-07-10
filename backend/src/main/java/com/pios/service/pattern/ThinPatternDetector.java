package com.pios.service.pattern;

import com.pios.common.AppTimeZones;
import com.pios.domain.GarminSleepSession;
import com.pios.repository.GarminSleepSessionRepository;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.MetricStatistic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ThinPatternDetector {

    private final GarminSleepSessionRepository sleepRepo;

    public List<DetectedPattern> detect(Long userId, EvidenceStatistics statistics, LocalDate periodEnd) {
        List<DetectedPattern> patterns = new ArrayList<>();

        findMetric(statistics, "sleepScore").ifPresent(m -> {
            if (m.isReliable() && m.getChangeRate() != null
                    && m.getChangeRate().compareTo(BigDecimal.valueOf(-15)) <= 0) {
                patterns.add(pattern("SLEEP_SCORE_DROP", "HIGH", m,
                        String.format("수면 점수가 기준선 대비 %.0f%% 하락했습니다", m.getChangeRate().abs())));
            }
        });

        findMetric(statistics, "restingHeartRate").ifPresent(m -> {
            if (m.isReliable() && m.getChangeRate() != null
                    && m.getChangeRate().compareTo(BigDecimal.valueOf(8)) >= 0) {
                patterns.add(pattern("RHR_ELEVATED", "HIGH", m,
                        String.format("안정시 심박이 기준선 대비 %.0f%% 상승했습니다", m.getChangeRate())));
            }
        });

        Optional<MetricStatistic> distance = findMetric(statistics, "totalDistanceMeters");
        Optional<MetricStatistic> activityCount = findMetric(statistics, "activityCount");
        Stream.concat(distance.stream(), activityCount.stream())
                .filter(m -> m.isReliable() && m.getChangeRate() != null
                        && m.getChangeRate().compareTo(BigDecimal.valueOf(40)) >= 0)
                .findFirst()
                .ifPresent(m -> patterns.add(pattern("VOLUME_SPIKE", "MED", m,
                        String.format("%s가 기준선 대비 %.0f%% 급증했습니다", m.getLabel(), m.getChangeRate()))));

        detectLowSleepStreak(userId, periodEnd).ifPresent(patterns::add);

        findMetric(statistics, "bodyBattery").ifPresent(m -> {
            if (m.isReliable() && m.getChangeRate() != null
                    && m.getChangeRate().compareTo(BigDecimal.valueOf(-20)) <= 0) {
                patterns.add(pattern("BODY_BATTERY_DROP", "MED", m,
                        String.format("바디 배터리가 기준선 대비 %.0f%% 하락했습니다", m.getChangeRate().abs())));
            }
        });

        return patterns.stream()
                .sorted(Comparator.comparingInt(ThinPatternDetector::severityRank))
                .limit(5)
                .toList();
    }

    private Optional<DetectedPattern> detectLowSleepStreak(Long userId, LocalDate periodEnd) {
        LocalDate end = periodEnd != null ? periodEnd : AppTimeZones.todayKst();
        LocalDate start = end.minusDays(2);
        List<GarminSleepSession> sleeps =
                sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(userId, start, end);
        if (sleeps.size() < 3) {
            return Optional.empty();
        }
        boolean allLow = sleeps.stream()
                .filter(s -> s.getTotalSleepSeconds() != null)
                .limit(3)
                .allMatch(s -> s.getTotalSleepSeconds() < 6 * 3600);
        if (!allLow) {
            return Optional.empty();
        }
        double avgHours = sleeps.stream()
                .limit(3)
                .mapToInt(GarminSleepSession::getTotalSleepSeconds)
                .average()
                .orElse(0) / 3600.0;
        return Optional.of(DetectedPattern.builder()
                .code("LOW_SLEEP_STREAK")
                .severity("MED")
                .metric("totalSleepSeconds")
                .currentValue(BigDecimal.valueOf(avgHours).setScale(2, java.math.RoundingMode.HALF_UP))
                .baselineValue(BigDecimal.valueOf(7))
                .changeRate(null)
                .unit("h")
                .humanHint("최근 3일 연속 수면이 6시간 미만입니다")
                .build());
    }

    private Optional<MetricStatistic> findMetric(EvidenceStatistics statistics, String metric) {
        return Stream.of(
                        statistics.getHealthMetrics(),
                        statistics.getSleepMetrics(),
                        statistics.getActivityMetrics()
                )
                .flatMap(List::stream)
                .filter(m -> metric.equals(m.getMetric()))
                .findFirst();
    }

    private DetectedPattern pattern(String code, String severity, MetricStatistic m, String hint) {
        return DetectedPattern.builder()
                .code(code)
                .severity(severity)
                .metric(m.getMetric())
                .currentValue(m.getCurrentValue())
                .baselineValue(m.getBaselineValue())
                .changeRate(m.getChangeRate())
                .unit(m.getUnit())
                .humanHint(hint)
                .build();
    }

    private static int severityRank(DetectedPattern p) {
        return switch (p.getSeverity()) {
            case "HIGH" -> 0;
            case "MED" -> 1;
            default -> 2;
        };
    }
}
