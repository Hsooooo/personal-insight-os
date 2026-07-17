package com.pios.service.goal;

import com.pios.common.AppTimeZones;
import com.pios.domain.Goal;
import com.pios.dto.AskPeriod;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.EvidenceStatisticsCalculator;
import com.pios.service.ask.MetricStatistic;
import com.pios.service.pattern.DetectedPattern;
import com.pios.service.pattern.ThinPatternDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Thin rule-based blockers for active goals (sleep / load / activity shortfall).
 */
@Component
@RequiredArgsConstructor
public class GoalBlockerAnalyzer {

    private final EvidenceStatisticsCalculator statisticsCalculator;
    private final ThinPatternDetector patternDetector;
    private final GoalProgressCalculator progressCalculator;

    public List<String> analyze(Goal goal) {
        if (goal == null || !"ACTIVE".equalsIgnoreCase(goal.getStatus())) {
            return List.of();
        }
        Optional<GoalType> typeOpt = GoalType.from(goal.getGoalType());
        if (typeOpt.isEmpty() || goal.getUser() == null || goal.getUser().getId() == null) {
            return List.of();
        }

        Long userId = goal.getUser().getId();
        LocalDate today = AppTimeZones.todayKst();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        AskPeriod period = AskPeriod.builder()
                .start(weekStart)
                .end(today)
                .baselineStart(weekStart.minusDays(28))
                .baselineEnd(weekStart.minusDays(1))
                .build();

        EvidenceStatistics stats = statisticsCalculator.calculate(userId, period);
        List<DetectedPattern> patterns = patternDetector.detect(userId, stats, today);
        var progress = progressCalculator.calculate(goal);

        List<String> blockers = new ArrayList<>();
        GoalType type = typeOpt.get();

        if (progress.isSupported()
                && progress.getProgressPercent() != null
                && progress.getProgressPercent().compareTo(BigDecimal.valueOf(50)) < 0
                && ("BEHIND".equals(progress.getPaceStatus()) || "AT_RISK".equals(progress.getPaceStatus()))) {
            blockers.add(String.format("목표 진행률 %s%%로 페이스가 뒤처져 있습니다",
                    progress.getProgressPercent().stripTrailingZeros().toPlainString()));
        }

        if (type == GoalType.SLEEP_HOURS || type == GoalType.WEEKLY_RUN_DISTANCE
                || type == GoalType.WEEKLY_ACTIVITY_COUNT) {
            findMetric(stats, "sleepScore").ifPresent(m -> {
                if (m.getChangeRate() != null && m.getChangeRate().compareTo(BigDecimal.valueOf(-10)) <= 0) {
                    blockers.add("수면 점수 하락이 회복·수행에 영향을 줄 수 있습니다");
                }
            });
            patterns.stream()
                    .filter(p -> "SLEEP_SCORE_DROP".equals(p.getCode()) || "LOW_SLEEP_STREAK".equals(p.getCode()))
                    .findFirst()
                    .ifPresent(p -> {
                        if (blockers.stream().noneMatch(b -> b.contains("수면"))) {
                            blockers.add(p.getHumanHint());
                        }
                    });
        }

        if (type == GoalType.WEEKLY_RUN_DISTANCE || type == GoalType.WEEKLY_ACTIVITY_COUNT) {
            patterns.stream()
                    .filter(p -> "VOLUME_SPIKE".equals(p.getCode()) || "RHR_ELEVATED".equals(p.getCode())
                            || "BODY_BATTERY_DROP".equals(p.getCode()))
                    .limit(1)
                    .forEach(p -> blockers.add(p.getHumanHint()));

            String metricName = type == GoalType.WEEKLY_RUN_DISTANCE ? "totalDistanceMeters" : "activityCount";
            findMetric(stats, metricName).ifPresent(m -> {
                if (m.getChangeRate() != null
                        && m.getChangeRate().compareTo(BigDecimal.valueOf(-20)) <= 0) {
                    blockers.add(String.format("%s가 기준선 대비 %.0f%% 감소했습니다",
                            m.getLabel(), m.getChangeRate().abs()));
                }
            });
        }

        return blockers.stream().distinct().limit(2).toList();
    }

    private Optional<MetricStatistic> findMetric(EvidenceStatistics stats, String metric) {
        return Stream.of(stats.getHealthMetrics(), stats.getSleepMetrics(), stats.getActivityMetrics())
                .filter(list -> list != null)
                .flatMap(List::stream)
                .filter(m -> metric.equals(m.getMetric()))
                .findFirst();
    }
}
