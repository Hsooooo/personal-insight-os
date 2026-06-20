package com.pios.service.ask;

import com.pios.domain.Activity;
import com.pios.domain.GarminDailyHealthMetric;
import com.pios.domain.GarminSleepSession;
import com.pios.dto.AskPeriod;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EvidenceStatisticsCalculator {

    private static final int RELIABLE_DAYS_THRESHOLD = 3;
    private static final int SCALE = 2;

    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final ActivityRepository activityRepo;

    public EvidenceStatistics calculate(Long userId, AskPeriod period) {
        List<GarminDailyHealthMetric> healthMetrics = healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
                userId, period.getBaselineStart(), period.getEnd());
        List<GarminSleepSession> sleepSessions = sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(
                userId, period.getBaselineStart(), period.getEnd());
        List<Activity> activities = activityRepo.findRecentByUserId(userId, period.getBaselineStart().atStartOfDay())
                .stream()
                .filter(a -> a.getStartTime() != null)
                .filter(a -> !toLocalDate(a.getStartTime()).isBefore(period.getBaselineStart())
                        && !toLocalDate(a.getStartTime()).isAfter(period.getEnd()))
                .toList();

        List<GarminDailyHealthMetric> currentHealth = filterByDate(healthMetrics, period.getStart(), period.getEnd());
        List<GarminDailyHealthMetric> baselineHealth = filterByDate(healthMetrics, period.getBaselineStart(), period.getBaselineEnd());

        List<GarminSleepSession> currentSleep = filterBySleepDate(sleepSessions, period.getStart(), period.getEnd());
        List<GarminSleepSession> baselineSleep = filterBySleepDate(sleepSessions, period.getBaselineStart(), period.getBaselineEnd());

        List<Activity> currentActivities = filterActivitiesByDate(activities, period.getStart(), period.getEnd());
        List<Activity> baselineActivities = filterActivitiesByDate(activities, period.getBaselineStart(), period.getBaselineEnd());

        return EvidenceStatistics.builder()
                .healthMetrics(List.of(
                        buildHealthAvg(currentHealth, baselineHealth, "restingHeartRate", "평균 안정시 심박", "bpm", GarminDailyHealthMetric::getRestingHeartRate),
                        buildHealthAvg(currentHealth, baselineHealth, "hrvAvg", "평균 HRV", "ms", GarminDailyHealthMetric::getHrvAvg),
                        buildHealthAvg(currentHealth, baselineHealth, "stressAvg", "평균 스트레스", "점", GarminDailyHealthMetric::getStressAvg),
                        buildBodyBattery(currentHealth, baselineHealth)
                ))
                .sleepMetrics(List.of(
                        buildSleepAvg(currentSleep, baselineSleep, "totalSleepSeconds", "평균 수면 시간", "시간", s -> doubleValue(s.getTotalSleepSeconds()), 3600.0),
                        buildSleepAvg(currentSleep, baselineSleep, "sleepScore", "평균 수면 점수", "점", GarminSleepSession::getSleepScore, 1.0),
                        buildSleepAvg(currentSleep, baselineSleep, "deepSleepSeconds", "평균 딥슬립", "시간", s -> doubleValue(s.getDeepSleepSeconds()), 3600.0),
                        buildSleepAvg(currentSleep, baselineSleep, "remSleepSeconds", "평균 REM 수면", "시간", s -> doubleValue(s.getRemSleepSeconds()), 3600.0)
                ))
                .activityMetrics(List.of(
                        buildActivityCount(currentActivities, baselineActivities),
                        buildActivitySum(currentActivities, baselineActivities, "totalDurationSeconds", "총 활동 시간", "시간", a -> doubleValue(a.getDurationSeconds()), 3600.0),
                        buildActivitySum(currentActivities, baselineActivities, "totalDistanceMeters", "총 활동 거리", "km", a -> doubleValue(a.getDistanceMeters()), 1000.0),
                        buildActivityAvg(currentActivities, baselineActivities, "averageHeartRate", "평균 활동 심박", "bpm", Activity::getAverageHeartRate),
                        buildTrainingDays(currentActivities, baselineActivities)
                ))
                .build();
    }

    private MetricStatistic buildHealthAvg(List<GarminDailyHealthMetric> current,
                                           List<GarminDailyHealthMetric> baseline,
                                           String metric, String label, String unit,
                                           java.util.function.Function<GarminDailyHealthMetric, Number> extractor) {
        BigDecimal currentAvg = avg(current, extractor);
        BigDecimal baselineAvg = avg(baseline, extractor);
        Optional<GarminDailyHealthMetric> latest = current.stream()
                .filter(h -> extractor.apply(h) != null)
                .max(Comparator.comparing(GarminDailyHealthMetric::getMetricDate));
        return buildMetric(metric, label, unit, currentAvg, baselineAvg,
                countNonNull(current, extractor), countNonNull(baseline, extractor),
                latest.map(GarminDailyHealthMetric::getId).orElse(null),
                latest.map(GarminDailyHealthMetric::getMetricDate).map(LocalDate::toString).orElse(null));
    }

    private MetricStatistic buildBodyBattery(List<GarminDailyHealthMetric> current,
                                             List<GarminDailyHealthMetric> baseline) {
        BigDecimal currentAvg = avg(current, h -> {
            if (h.getBodyBatteryMin() == null || h.getBodyBatteryMax() == null) return null;
            return (h.getBodyBatteryMin() + h.getBodyBatteryMax()) / 2.0;
        });
        BigDecimal baselineAvg = avg(baseline, h -> {
            if (h.getBodyBatteryMin() == null || h.getBodyBatteryMax() == null) return null;
            return (h.getBodyBatteryMin() + h.getBodyBatteryMax()) / 2.0;
        });
        Optional<GarminDailyHealthMetric> latest = current.stream()
                .filter(h -> h.getBodyBatteryMin() != null && h.getBodyBatteryMax() != null)
                .max(Comparator.comparing(GarminDailyHealthMetric::getMetricDate));
        return buildMetric("bodyBattery", "평균 바디 배터리", "점", currentAvg, baselineAvg,
                countNonNull(current, h -> h.getBodyBatteryMin() != null && h.getBodyBatteryMax() != null ? 1 : null),
                countNonNull(baseline, h -> h.getBodyBatteryMin() != null && h.getBodyBatteryMax() != null ? 1 : null),
                latest.map(GarminDailyHealthMetric::getId).orElse(null),
                latest.map(GarminDailyHealthMetric::getMetricDate).map(LocalDate::toString).orElse(null));
    }

    private MetricStatistic buildSleepAvg(List<GarminSleepSession> current,
                                          List<GarminSleepSession> baseline,
                                          String metric, String label, String unit,
                                          java.util.function.Function<GarminSleepSession, Number> extractor,
                                          double divisor) {
        BigDecimal currentAvg = avg(current, extractor);
        BigDecimal baselineAvg = avg(baseline, extractor);
        if (currentAvg != null && divisor != 1.0) {
            currentAvg = currentAvg.divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
        }
        if (baselineAvg != null && divisor != 1.0) {
            baselineAvg = baselineAvg.divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
        }
        Optional<GarminSleepSession> latest = current.stream()
                .filter(s -> extractor.apply(s) != null)
                .max(Comparator.comparing(GarminSleepSession::getSleepDate));
        return buildMetric(metric, label, unit, currentAvg, baselineAvg,
                countNonNull(current, extractor), countNonNull(baseline, extractor),
                latest.map(GarminSleepSession::getId).orElse(null),
                latest.map(GarminSleepSession::getSleepDate).map(LocalDate::toString).orElse(null));
    }

    private MetricStatistic buildActivityCount(List<Activity> current, List<Activity> baseline) {
        Optional<Activity> latest = current.stream().max(Comparator.comparing(Activity::getStartTime));
        return buildMetric("activityCount", "활동 횟수", "회",
                BigDecimal.valueOf(current.size()), BigDecimal.valueOf(baseline.size()),
                current.size(), baseline.size(),
                latest.map(Activity::getId).orElse(null),
                latest.map(a -> toLocalDate(a.getStartTime()).toString()).orElse(null));
    }

    private MetricStatistic buildActivitySum(List<Activity> current, List<Activity> baseline,
                                             String metric, String label, String unit,
                                             java.util.function.Function<Activity, Number> extractor,
                                             double divisor) {
        BigDecimal currentSum = sum(current, extractor);
        BigDecimal baselineSum = sum(baseline, extractor);
        if (currentSum != null && divisor != 1.0) {
            currentSum = currentSum.divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
        }
        if (baselineSum != null && divisor != 1.0) {
            baselineSum = baselineSum.divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
        }
        Optional<Activity> latest = current.stream().max(Comparator.comparing(Activity::getStartTime));
        return buildMetric(metric, label, unit, currentSum, baselineSum,
                current.size(), baseline.size(),
                latest.map(Activity::getId).orElse(null),
                latest.map(a -> toLocalDate(a.getStartTime()).toString()).orElse(null));
    }

    private MetricStatistic buildActivityAvg(List<Activity> current, List<Activity> baseline,
                                             String metric, String label, String unit,
                                             java.util.function.Function<Activity, Number> extractor) {
        BigDecimal currentAvg = avg(current, extractor);
        BigDecimal baselineAvg = avg(baseline, extractor);
        Optional<Activity> latest = current.stream()
                .filter(a -> extractor.apply(a) != null)
                .max(Comparator.comparing(Activity::getStartTime));
        return buildMetric(metric, label, unit, currentAvg, baselineAvg,
                countNonNull(current, extractor), countNonNull(baseline, extractor),
                latest.map(Activity::getId).orElse(null),
                latest.map(a -> toLocalDate(a.getStartTime()).toString()).orElse(null));
    }

    private MetricStatistic buildTrainingDays(List<Activity> current, List<Activity> baseline) {
        Set<LocalDate> currentDays = current.stream()
                .map(a -> toLocalDate(a.getStartTime()))
                .collect(Collectors.toSet());
        Set<LocalDate> baselineDays = baseline.stream()
                .map(a -> toLocalDate(a.getStartTime()))
                .collect(Collectors.toSet());
        Optional<Activity> latest = current.stream().max(Comparator.comparing(Activity::getStartTime));
        return buildMetric("trainingDays", "훈련일 수", "일",
                BigDecimal.valueOf(currentDays.size()), BigDecimal.valueOf(baselineDays.size()),
                currentDays.size(), baselineDays.size(),
                latest.map(Activity::getId).orElse(null),
                latest.map(a -> toLocalDate(a.getStartTime()).toString()).orElse(null));
    }

    private MetricStatistic buildMetric(String metric, String label, String unit,
                                        BigDecimal currentValue, BigDecimal baselineValue,
                                        int currentDays, int baselineDays,
                                        Long sourceId, String sourceDate) {
        return MetricStatistic.builder()
                .metric(metric)
                .label(label)
                .currentValue(currentValue)
                .baselineValue(baselineValue)
                .changeRate(changeRate(currentValue, baselineValue))
                .unit(unit)
                .currentDays(currentDays)
                .baselineDays(baselineDays)
                .reliable(currentDays >= RELIABLE_DAYS_THRESHOLD)
                .sourceId(sourceId)
                .sourceDate(sourceDate)
                .build();
    }

    private <T> BigDecimal avg(List<T> items, java.util.function.Function<T, Number> extractor) {
        List<Double> values = items.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .filter(d -> !Double.isNaN(d))
                .toList();
        if (values.isEmpty()) return null;
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return BigDecimal.valueOf(sum / values.size()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private <T> BigDecimal sum(List<T> items, java.util.function.Function<T, Number> extractor) {
        List<Double> values = items.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .filter(d -> !Double.isNaN(d))
                .toList();
        if (values.isEmpty()) return null;
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return BigDecimal.valueOf(sum).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private <T> int countNonNull(List<T> items, java.util.function.Function<T, Number> extractor) {
        return (int) items.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .count();
    }

    private BigDecimal changeRate(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(baseline)
                .divide(baseline, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate toLocalDate(LocalDateTime dateTime) {
        return dateTime.toLocalDate();
    }

    private List<GarminDailyHealthMetric> filterByDate(List<GarminDailyHealthMetric> metrics, LocalDate start, LocalDate end) {
        return metrics.stream()
                .filter(m -> !m.getMetricDate().isBefore(start) && !m.getMetricDate().isAfter(end))
                .toList();
    }

    private List<GarminSleepSession> filterBySleepDate(List<GarminSleepSession> sessions, LocalDate start, LocalDate end) {
        return sessions.stream()
                .filter(s -> !s.getSleepDate().isBefore(start) && !s.getSleepDate().isAfter(end))
                .toList();
    }

    private List<Activity> filterActivitiesByDate(List<Activity> activities, LocalDate start, LocalDate end) {
        return activities.stream()
                .filter(a -> !toLocalDate(a.getStartTime()).isBefore(start) && !toLocalDate(a.getStartTime()).isAfter(end))
                .toList();
    }

    private static Double doubleValue(Number value) {
        return value != null ? value.doubleValue() : null;
    }
}
