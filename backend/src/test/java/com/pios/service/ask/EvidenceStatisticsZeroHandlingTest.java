package com.pios.service.ask;

import com.pios.domain.GarminDailyHealthMetric;
import com.pios.domain.GarminSleepSession;
import com.pios.domain.User;
import com.pios.dto.AskPeriod;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import com.pios.service.pattern.DetectedPattern;
import com.pios.service.pattern.ThinPatternDetector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 과거 동기화가 누락값을 0으로 저장한 행이 평균/기준선을 오염시키지 않는지 검증.
 */
class EvidenceStatisticsZeroHandlingTest {

    private final LocalDate today = LocalDate.of(2026, 6, 20);
    private final AskPeriod period = AskPeriod.builder()
            .start(today.minusDays(1)).end(today)
            .baselineStart(today.minusDays(5)).baselineEnd(today.minusDays(2))
            .build();

    private GarminDailyHealthMetric health(long id, LocalDate date, Integer rhr, Double hrv) {
        return GarminDailyHealthMetric.builder().id(id).user(User.builder().id(1L).build()).metricDate(date)
                .restingHeartRate(rhr).hrvAvg(hrv != null ? BigDecimal.valueOf(hrv) : null).build();
    }

    private GarminSleepSession sleep(long id, LocalDate date, Integer total, Integer score) {
        return GarminSleepSession.builder().id(id).user(User.builder().id(1L).build()).sleepDate(date)
                .totalSleepSeconds(total).sleepScore(score).build();
    }

    private MetricStatistic find(List<MetricStatistic> list, String metric) {
        return list.stream().filter(m -> metric.equals(m.getMetric())).findFirst().orElseThrow();
    }

    @Test
    void zerosAreIgnoredForPhysiologicalMetrics() {
        var healthRepo = mock(GarminDailyHealthMetricRepository.class);
        var sleepRepo = mock(GarminSleepSessionRepository.class);
        var activityRepo = mock(ActivityRepository.class);
        when(healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(eq(1L), any(), any())).thenReturn(List.of(
                health(1, today, 60, 50.0),
                health(2, today.minusDays(1), 0, 0.0),          // 누락값이 0으로 저장된 행
                health(3, today.minusDays(2), 62, 48.0),
                health(4, today.minusDays(3), 0, 0.0)));
        when(sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(eq(1L), any(), any())).thenReturn(List.of(
                sleep(1, today, 7 * 3600, 80),
                sleep(2, today.minusDays(1), 0, 0),
                sleep(3, today.minusDays(2), 6 * 3600, 70)));
        when(activityRepo.findRecentByUserId(eq(1L), any())).thenReturn(List.of());

        EvidenceStatistics stats = new EvidenceStatisticsCalculator(healthRepo, sleepRepo, activityRepo)
                .calculate(1L, period);

        MetricStatistic rhr = find(stats.getHealthMetrics(), "restingHeartRate");
        assertThat(rhr.getCurrentValue()).isEqualByComparingTo("60");
        assertThat(rhr.getCurrentDays()).isEqualTo(1);
        assertThat(rhr.getBaselineValue()).isEqualByComparingTo("62");
        assertThat(find(stats.getHealthMetrics(), "hrvAvg").getCurrentValue()).isEqualByComparingTo("50");
        assertThat(find(stats.getSleepMetrics(), "sleepScore").getCurrentValue()).isEqualByComparingTo("80");
        assertThat(find(stats.getSleepMetrics(), "totalSleepSeconds").getCurrentValue()).isEqualByComparingTo("7");
    }

    @Test
    void lowSleepStreakIgnoresZeroAndNullSessions() {
        var sleepRepo = mock(GarminSleepSessionRepository.class);
        when(sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(eq(1L), any(), any())).thenReturn(List.of(
                sleep(1, today, 5 * 3600, 60),
                sleep(2, today.minusDays(1), 0, 0),
                sleep(3, today.minusDays(2), null, null)));
        EvidenceStatistics empty = EvidenceStatistics.builder()
                .healthMetrics(List.of()).sleepMetrics(List.of()).activityMetrics(List.of()).build();

        List<DetectedPattern> patterns = new ThinPatternDetector(sleepRepo).detect(1L, empty, today);
        // 유효한 수면이 1일뿐이므로 "연속 수면 부족"으로 판단하지 않는다 (이전엔 null에서 NPE 가능)
        assertThat(patterns).noneMatch(p -> "LOW_SLEEP_STREAK".equals(p.getCode()));
    }
}
