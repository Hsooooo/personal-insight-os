package com.pios.service.goal;

import com.pios.common.AppTimeZones;
import com.pios.domain.Activity;
import com.pios.domain.Goal;
import com.pios.domain.User;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalProgressCalculatorTest {

    private final ActivityRepository activityRepo = mock(ActivityRepository.class);
    private final GoalProgressCalculator calculator = new GoalProgressCalculator(
            activityRepo, mock(GarminDailyHealthMetricRepository.class), mock(GarminSleepSessionRepository.class));

    private static Goal raceGoal(String distanceKm, int targetSeconds) {
        return Goal.builder()
                .user(User.builder().id(1L).build())
                .title("하프 1:35 under")
                .goalType("RACE")
                .raceDistanceKm(distanceKm != null ? new BigDecimal(distanceKm) : null)
                .targetValue(BigDecimal.valueOf(targetSeconds))
                .status("ACTIVE")
                .build();
    }

    private static Activity run(String type, double meters, int seconds) {
        return Activity.builder()
                .activityType(type)
                .startTime(AppTimeZones.todayKst().minusDays(3).atTime(7, 0))
                .distanceMeters(BigDecimal.valueOf(meters))
                .durationSeconds(seconds)
                .build();
    }

    @Test
    void racePredictsFinishTimeFromBestRecentRun() {
        // 10km 42:00 → 하프 예상 ≈ 42:00 × 2.10975^1.06 ≈ 1:32:39
        when(activityRepo.findRecentByUserId(eq(1L), any())).thenReturn(List.of(
                run("running", 10_000, 42 * 60),
                run("running", 8_000, 45 * 60),        // 느린 조깅: 무시됨 (더 나쁜 예측)
                run("running", 2_000, 6 * 60),         // 3km 미만: 제외
                run("cycling", 40_000, 60 * 60)));     // 러닝 아님: 제외

        var result = calculator.calculate(raceGoal("21.0975", 95 * 60));

        assertThat(result.isSupported()).isTrue();
        double expected = GoalProgressCalculator.predictSeconds(42 * 60, 10_000, 21_097.5);
        assertThat(result.getCurrentValue()).isEqualByComparingTo(BigDecimal.valueOf(Math.round(expected)));
        assertThat(result.getPaceStatus()).isEqualTo("AHEAD");
        assertThat(result.getProgressPercent()).isGreaterThan(BigDecimal.valueOf(100));
    }

    @Test
    void raceIsBehindWhenPredictionIsSlowerThanTarget() {
        when(activityRepo.findRecentByUserId(eq(1L), any())).thenReturn(List.of(run("running", 12_000, 70 * 60)));

        var result = calculator.calculate(raceGoal("42.195", 210 * 60));

        assertThat(result.getPaceStatus()).isEqualTo("BEHIND");
        assertThat(result.getProgressPercent()).isLessThan(BigDecimal.valueOf(100));
    }

    @Test
    void raceWithoutDistanceOrRunsIsInsufficientData() {
        assertThat(calculator.calculate(raceGoal(null, 95 * 60)).getPaceStatus()).isEqualTo("INSUFFICIENT_DATA");

        when(activityRepo.findRecentByUserId(eq(1L), any())).thenReturn(List.of());
        var result = calculator.calculate(raceGoal("21.0975", 95 * 60));
        assertThat(result.isSupported()).isTrue();
        assertThat(result.getPaceStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.getCurrentValue()).isNull();
    }
}
