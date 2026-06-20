package com.pios.service.ask;

import com.pios.dto.AskConfidence;
import com.pios.dto.AskPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceScorerTest {

    @Test
    void highConfidenceWithFullDataAndBaseline() {
        AskPeriod period = period(7, 28);
        EvidenceStatistics stats = stats(7, 28, 7, 28, 7, 28);
        AskConfidence c = ConfidenceScorer.score(period, stats, AskIntent.CONDITION);
        assertThat(c.getScore()).isGreaterThan(new BigDecimal("0.75"));
        assertThat(c.getLevel()).isEqualTo("HIGH");
        assertThat(c.getReasons()).anyMatch(r -> r.contains("확보"));
    }

    @Test
    void lowConfidenceWhenNoData() {
        AskPeriod period = period(7, 28);
        EvidenceStatistics stats = stats(0, 0, 0, 0, 0, 0);
        AskConfidence c = ConfidenceScorer.score(period, stats, AskIntent.CONDITION);
        assertThat(c.getLevel()).isEqualTo("LOW");
    }

    @Test
    void mediumConfidenceWithPartialData() {
        AskPeriod period = period(7, 28);
        EvidenceStatistics stats = stats(2, 10, 2, 10, 2, 10);
        AskConfidence c = ConfidenceScorer.score(period, stats, AskIntent.CONDITION);
        assertThat(c.getLevel()).isIn("LOW", "MEDIUM");
    }

    @Test
    void relevanceBoostsWhenIntentMetricsReliable() {
        AskPeriod period = period(7, 28);
        EvidenceStatistics stats = EvidenceStatistics.builder()
                .healthMetrics(List.of(metric("hrvAvg", 7, 28, true)))
                .sleepMetrics(List.of())
                .activityMetrics(List.of())
                .build();
        AskConfidence c = ConfidenceScorer.score(period, stats, AskIntent.CONDITION);
        assertThat(c.getReasons()).anyMatch(r -> r.contains("관련된 지표"));
    }

    private AskPeriod period(int analysisDays, int baselineDays) {
        LocalDate end = LocalDate.of(2026, 6, 20);
        return AskPeriod.builder()
                .end(end)
                .start(end.minusDays(analysisDays - 1))
                .baselineEnd(end.minusDays(analysisDays))
                .baselineStart(end.minusDays(analysisDays + baselineDays - 1))
                .build();
    }

    private EvidenceStatistics stats(int healthCurrent, int healthBaseline, int sleepCurrent, int sleepBaseline,
                                      int activityCurrent, int activityBaseline) {
        return EvidenceStatistics.builder()
                .healthMetrics(List.of(
                        metric("restingHeartRate", healthCurrent, healthBaseline, healthCurrent >= 3),
                        metric("hrvAvg", healthCurrent, healthBaseline, healthCurrent >= 3),
                        metric("stressAvg", healthCurrent, healthBaseline, healthCurrent >= 3),
                        metric("bodyBattery", healthCurrent, healthBaseline, healthCurrent >= 3)
                ))
                .sleepMetrics(List.of(
                        metric("totalSleepSeconds", sleepCurrent, sleepBaseline, sleepCurrent >= 3),
                        metric("sleepScore", sleepCurrent, sleepBaseline, sleepCurrent >= 3),
                        metric("deepSleepSeconds", sleepCurrent, sleepBaseline, sleepCurrent >= 3),
                        metric("remSleepSeconds", sleepCurrent, sleepBaseline, sleepCurrent >= 3)
                ))
                .activityMetrics(List.of(
                        metric("activityCount", activityCurrent, activityBaseline, activityCurrent >= 3),
                        metric("totalDurationSeconds", activityCurrent, activityBaseline, activityCurrent >= 3),
                        metric("trainingDays", activityCurrent, activityBaseline, activityCurrent >= 3)
                ))
                .build();
    }

    private MetricStatistic metric(String metric, int currentDays, int baselineDays, boolean reliable) {
        return MetricStatistic.builder()
                .metric(metric)
                .label(metric)
                .currentValue(BigDecimal.valueOf(currentDays))
                .baselineValue(BigDecimal.valueOf(baselineDays))
                .changeRate(BigDecimal.ZERO)
                .unit("")
                .currentDays(currentDays)
                .baselineDays(baselineDays)
                .reliable(reliable)
                .build();
    }
}
