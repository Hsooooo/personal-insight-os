package com.pios.service.ask;

import com.pios.domain.Activity;
import com.pios.domain.GarminDailyHealthMetric;
import com.pios.domain.GarminSleepSession;
import com.pios.domain.User;
import com.pios.dto.AskPeriod;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceStatisticsCalculatorTest {

    @Mock
    private GarminDailyHealthMetricRepository healthRepo;
    @Mock
    private GarminSleepSessionRepository sleepRepo;
    @Mock
    private ActivityRepository activityRepo;

    @InjectMocks
    private EvidenceStatisticsCalculator calculator;

    @Test
    void averagesAndChangeRatesAreCalculated() {
        LocalDate today = LocalDate.of(2026, 6, 20);
        AskPeriod period = AskPeriod.builder()
                .start(today.minusDays(1))
                .end(today)
                .baselineStart(today.minusDays(5))
                .baselineEnd(today.minusDays(2))
                .build();

        when(healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of(
                        health(1L, today, 60, 50.0, 30.0, 10, 90),
                        health(2L, today.minusDays(1), 62, 48.0, 32.0, 20, 80),
                        health(3L, today.minusDays(2), 63, 45.0, 35.0, 15, 85),
                        health(4L, today.minusDays(3), 64, 44.0, 33.0, 12, 88),
                        health(5L, today.minusDays(4), 61, 46.0, 31.0, 18, 82),
                        health(6L, today.minusDays(5), 60, 47.0, 30.0, 20, 80)
                ));
        when(sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of(
                        sleep(1L, today, 28800, 85, 7200, 5400),
                        sleep(2L, today.minusDays(1), 27000, 80, 6000, 4800),
                        sleep(3L, today.minusDays(2), 25200, 78, 5400, 4200),
                        sleep(4L, today.minusDays(3), 28800, 82, 6600, 5100)
                ));
        when(activityRepo.findRecentByUserId(eq(1L), any()))
                .thenReturn(List.of(
                        activity(1L, today.atTime(8, 0), 3600, 5000.0, 140),
                        activity(2L, today.minusDays(1).atTime(8, 0), 2700, 4000.0, 135)
                ));

        EvidenceStatistics stats = calculator.calculate(1L, period);

        MetricStatistic hrv = find(stats.getHealthMetrics(), "hrvAvg");
        assertThat(hrv.getCurrentValue()).isEqualByComparingTo(new BigDecimal("49.00"));
        assertThat(hrv.getBaselineValue()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(hrv.getChangeRate()).isGreaterThan(new BigDecimal("7"));
        assertThat(hrv.isReliable()).isFalse();

        MetricStatistic sleepTime = find(stats.getSleepMetrics(), "totalSleepSeconds");
        assertThat(sleepTime.getCurrentValue()).isEqualByComparingTo(new BigDecimal("7.75")); // hours
        assertThat(sleepTime.getUnit()).isEqualTo("시간");

        MetricStatistic activityCount = find(stats.getActivityMetrics(), "activityCount");
        assertThat(activityCount.getCurrentValue()).isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void nullSleepSecondsAreHandled() {
        LocalDate today = LocalDate.of(2026, 6, 20);
        AskPeriod period = AskPeriod.builder()
                .start(today.minusDays(6))
                .end(today)
                .baselineStart(today.minusDays(13))
                .baselineEnd(today.minusDays(7))
                .build();

        when(healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of(
                        sleep(1L, today, 28800, 85, 7200, 5400),
                        sleep(2L, today.minusDays(1), null, 80, 6000, 4800),
                        sleep(3L, today.minusDays(2), 25200, 78, null, 4200)
                ));
        when(activityRepo.findRecentByUserId(eq(1L), any())).thenReturn(List.of());

        EvidenceStatistics stats = calculator.calculate(1L, period);

        MetricStatistic totalSleep = find(stats.getSleepMetrics(), "totalSleepSeconds");
        assertThat(totalSleep.getCurrentValue()).isNotNull();
        MetricStatistic deepSleep = find(stats.getSleepMetrics(), "deepSleepSeconds");
        assertThat(deepSleep.getCurrentValue()).isNotNull();
    }

    @Test
    void nullValuesAreExcludedAndUnreliableBelowThreeDays() {
        LocalDate today = LocalDate.of(2026, 6, 20);
        AskPeriod period = AskPeriod.builder()
                .start(today.minusDays(1))
                .end(today)
                .baselineStart(today.minusDays(5))
                .baselineEnd(today.minusDays(2))
                .build();

        when(healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of(
                        health(1L, today, null, 50.0, null, 10, 90),
                        health(2L, today.minusDays(1), 62, null, 32.0, 20, 80),
                        health(3L, today.minusDays(2), 63, 45.0, 35.0, 15, 85)
                ));
        when(sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(activityRepo.findRecentByUserId(eq(1L), any())).thenReturn(List.of());

        EvidenceStatistics stats = calculator.calculate(1L, period);

        MetricStatistic hrv = find(stats.getHealthMetrics(), "hrvAvg");
        assertThat(hrv.getCurrentValue()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(hrv.getCurrentDays()).isEqualTo(1);
        assertThat(hrv.isReliable()).isFalse();
    }

    private MetricStatistic find(List<MetricStatistic> metrics, String metric) {
        return metrics.stream().filter(m -> m.getMetric().equals(metric)).findFirst().orElseThrow();
    }

    private GarminDailyHealthMetric health(Long id, LocalDate date, Integer rhr, Double hrv, Double stress,
                                            Integer bbMin, Integer bbMax) {
        return GarminDailyHealthMetric.builder()
                .id(id)
                .user(User.builder().id(1L).build())
                .metricDate(date)
                .restingHeartRate(rhr)
                .hrvAvg(hrv != null ? BigDecimal.valueOf(hrv) : null)
                .stressAvg(stress != null ? BigDecimal.valueOf(stress) : null)
                .bodyBatteryMin(bbMin)
                .bodyBatteryMax(bbMax)
                .build();
    }

    private GarminSleepSession sleep(Long id, LocalDate date, Integer total, Integer score, Integer deep, Integer rem) {
        return GarminSleepSession.builder()
                .id(id)
                .user(User.builder().id(1L).build())
                .sleepDate(date)
                .totalSleepSeconds(total)
                .sleepScore(score)
                .deepSleepSeconds(deep)
                .remSleepSeconds(rem)
                .build();
    }

    private Activity activity(Long id, LocalDateTime startTime, int duration, double distance, int avgHr) {
        return Activity.builder()
                .id(id)
                .user(User.builder().id(1L).build())
                .startTime(startTime)
                .durationSeconds(duration)
                .distanceMeters(BigDecimal.valueOf(distance))
                .averageHeartRate(avgHr)
                .build();
    }
}
