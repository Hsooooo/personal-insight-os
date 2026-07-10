package com.pios.service.goal;

import com.pios.common.AppTimeZones;
import com.pios.domain.Activity;
import com.pios.domain.GarminDailyHealthMetric;
import com.pios.domain.GarminSleepSession;
import com.pios.domain.Goal;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class GoalProgressCalculator {

    private static final Set<String> RUN_TYPES = Set.of(
            "RUNNING", "TRAIL_RUNNING", "TREADMILL_RUNNING", "TRACK_RUNNING",
            "VIRTUAL_RUN", "INDOOR_RUNNING", "RUN"
    );

    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;

    public ProgressResult calculate(Goal goal) {
        return GoalType.from(goal.getGoalType())
                .map(type -> calculateTracked(goal, type))
                .orElse(ProgressResult.unsupported());
    }

    private ProgressResult calculateTracked(Goal goal, GoalType type) {
        LocalDate today = AppTimeZones.todayKst();
        LocalDate start = goal.getStartDate() != null ? goal.getStartDate() : today.minusDays(6);
        LocalDate end = today;
        if (type == GoalType.WEEKLY_RUN_DISTANCE || type == GoalType.WEEKLY_ACTIVITY_COUNT) {
            // 주간 목표는 이번 주(월~오늘) 기준으로 집계
            start = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        } else if (type == GoalType.SLEEP_HOURS) {
            start = today.minusDays(6);
        }

        BigDecimal current = switch (type) {
            case WEEKLY_RUN_DISTANCE -> sumRunDistanceKm(goal.getUser().getId(), start, end);
            case WEEKLY_ACTIVITY_COUNT -> BigDecimal.valueOf(countActivities(goal.getUser().getId(), start, end));
            case SLEEP_HOURS -> avgSleepHours(goal.getUser().getId(), start, end);
            case WEIGHT_KG -> latestWeight(goal.getUser().getId());
        };

        if (current == null || goal.getTargetValue() == null
                || goal.getTargetValue().compareTo(BigDecimal.ZERO) == 0) {
            return ProgressResult.builder()
                    .supported(true)
                    .currentValue(current)
                    .progressPercent(null)
                    .paceStatus("INSUFFICIENT_DATA")
                    .build();
        }

        BigDecimal progressPercent = current
                .multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetValue(), 1, RoundingMode.HALF_UP);

        // 체중은 감량 목표(현재 > 목표)와 증량 목표를 단순 비율로 처리하기 어려워
        // "목표에 얼마나 가까운지" 대신 현재/목표 비율을 그대로 노출하고 pace는 날짜 기준으로만 본다.
        String paceStatus = resolvePace(goal, progressPercent, today, type);
        LocalDate projectedDate = projectDate(goal, current, today, type);
        String warning = resolveWarning(goal, progressPercent, today, type);

        return ProgressResult.builder()
                .supported(true)
                .currentValue(current)
                .progressPercent(progressPercent)
                .paceStatus(paceStatus)
                .projectedDate(projectedDate)
                .warning(warning)
                .build();
    }

    private BigDecimal sumRunDistanceKm(Long userId, LocalDate start, LocalDate end) {
        List<Activity> activities = activityRepo.findRecentByUserId(userId, start.atStartOfDay());
        double meters = activities.stream()
                .filter(a -> a.getStartTime() != null)
                .filter(a -> {
                    LocalDate d = a.getStartTime().toLocalDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .filter(this::isRunning)
                .filter(a -> a.getDistanceMeters() != null)
                .mapToDouble(a -> a.getDistanceMeters().doubleValue())
                .sum();
        return BigDecimal.valueOf(meters / 1000.0).setScale(2, RoundingMode.HALF_UP);
    }

    private long countActivities(Long userId, LocalDate start, LocalDate end) {
        return activityRepo.findRecentByUserId(userId, start.atStartOfDay()).stream()
                .filter(a -> a.getStartTime() != null)
                .filter(a -> {
                    LocalDate d = a.getStartTime().toLocalDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .count();
    }

    private BigDecimal avgSleepHours(Long userId, LocalDate start, LocalDate end) {
        List<GarminSleepSession> sleeps =
                sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(userId, start, end);
        if (sleeps.isEmpty()) {
            return null;
        }
        double avgSeconds = sleeps.stream()
                .filter(s -> s.getTotalSleepSeconds() != null)
                .mapToInt(GarminSleepSession::getTotalSleepSeconds)
                .average()
                .orElse(Double.NaN);
        if (Double.isNaN(avgSeconds)) {
            return null;
        }
        return BigDecimal.valueOf(avgSeconds / 3600.0).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal latestWeight(Long userId) {
        return healthRepo.findLatestByUserId(userId)
                .map(GarminDailyHealthMetric::getWeightKg)
                .orElse(null);
    }

    private boolean isRunning(Activity a) {
        if (a.getActivityType() == null) {
            return false;
        }
        String type = a.getActivityType().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (RUN_TYPES.contains(type)) {
            return true;
        }
        return type.contains("RUN");
    }

    private String resolvePace(Goal goal, BigDecimal progressPercent, LocalDate today, GoalType type) {
        if (goal.getStartDate() == null || goal.getTargetDate() == null
                || !goal.getTargetDate().isAfter(goal.getStartDate())) {
            if (progressPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
                return "AHEAD";
            }
            if (progressPercent.compareTo(BigDecimal.valueOf(70)) >= 0) {
                return "ON_TRACK";
            }
            return "BEHIND";
        }

        long totalDays = ChronoUnit.DAYS.between(goal.getStartDate(), goal.getTargetDate());
        long elapsedDays = ChronoUnit.DAYS.between(goal.getStartDate(), today);
        if (totalDays <= 0) {
            return "INSUFFICIENT_DATA";
        }
        elapsedDays = Math.max(elapsedDays, 0);
        double expectedPercent = (elapsedDays / (double) totalDays) * 100.0;

        // 체중 목표는 진행률 해석이 다르므로 날짜 기대치와 ±15%p 비교만
        double actual = progressPercent.doubleValue();
        if (type == GoalType.WEIGHT_KG) {
            return "ON_TRACK";
        }
        if (actual >= expectedPercent + 15) {
            return "AHEAD";
        }
        if (actual <= expectedPercent - 15) {
            return "BEHIND";
        }
        return "ON_TRACK";
    }

    private LocalDate projectDate(Goal goal, BigDecimal current, LocalDate today, GoalType type) {
        if (goal.getTargetValue() == null || current == null
                || type == GoalType.WEIGHT_KG || type == GoalType.SLEEP_HOURS) {
            return null;
        }
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        LocalDate start = goal.getStartDate() != null ? goal.getStartDate() : today.minusDays(6);
        long elapsed = Math.max(ChronoUnit.DAYS.between(start, today), 1);
        double dailyRate = current.doubleValue() / elapsed;
        if (dailyRate <= 0) {
            return null;
        }
        double remaining = goal.getTargetValue().doubleValue() - current.doubleValue();
        if (remaining <= 0) {
            return today;
        }
        long daysNeeded = (long) Math.ceil(remaining / dailyRate);
        return today.plusDays(daysNeeded);
    }

    private String resolveWarning(Goal goal, BigDecimal progressPercent, LocalDate today, GoalType type) {
        if (type != GoalType.WEEKLY_RUN_DISTANCE && type != GoalType.WEEKLY_ACTIVITY_COUNT) {
            return null;
        }
        if (progressPercent.compareTo(BigDecimal.valueOf(150)) >= 0) {
            return "OVERTRAINING_HINT";
        }
        if (goal.getStartDate() != null && goal.getTargetDate() != null
                && goal.getTargetDate().isAfter(goal.getStartDate())) {
            long total = ChronoUnit.DAYS.between(goal.getStartDate(), goal.getTargetDate());
            long elapsed = ChronoUnit.DAYS.between(goal.getStartDate(), today);
            if (total > 0 && elapsed >= total * 0.5
                    && progressPercent.compareTo(BigDecimal.valueOf(50)) < 0) {
                return "UNDERTRAINING_HINT";
            }
        }
        return null;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProgressResult {
        private boolean supported;
        private BigDecimal currentValue;
        private BigDecimal progressPercent;
        private String paceStatus;
        private LocalDate projectedDate;
        private String warning;

        public static ProgressResult unsupported() {
            return ProgressResult.builder().supported(false).paceStatus("UNSUPPORTED").build();
        }
    }
}
