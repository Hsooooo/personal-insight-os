package com.pios.service;

import com.pios.domain.*;
import com.pios.dto.*;
import com.pios.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final InsightRepository insightRepo;

    public DashboardSummaryDto getSummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        LocalDateTime weekAgoDateTime = weekAgo.atStartOfDay();

        var latestHealth = healthRepo.findLatestByUserId(userId).map(this::toHealthDto).orElse(null);
        var latestSleep = sleepRepo.findLatestByUserId(userId).map(this::toSleepDto).orElse(null);
        var latestActivity = activityRepo.findRecentByUserId(userId, weekAgoDateTime).stream()
                .findFirst().map(this::toActivityDto).orElse(null);
        var totalActivities = activityRepo.countByUserId(userId);

        var last7Health = healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(userId, weekAgo, today)
                .stream().map(this::toHealthDto).toList();
        var last7Activities = activityRepo.findRecentByUserId(userId, weekAgoDateTime)
                .stream().map(this::toActivityDto).toList();
        var recentInsights = insightRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(3).map(this::toInsightDto).toList();

        return DashboardSummaryDto.builder()
                .latestHealth(latestHealth)
                .latestSleep(latestSleep)
                .latestActivity(latestActivity)
                .totalActivities(totalActivities)
                .last7DaysHealth(last7Health)
                .last7DaysActivities(last7Activities)
                .recentInsights(recentInsights)
                .suggestedQuestions(List.of(
                        "최근 컨디션이 안 좋은 이유는?",
                        "이번 주 훈련 강도는 적절해?",
                        "러닝 기록이 좋았던 날들의 공통점은?"
                ))
                .build();
    }

    private HealthMetricDto toHealthDto(GarminDailyHealthMetric h) {
        return HealthMetricDto.builder()
                .id(h.getId()).metricDate(h.getMetricDate())
                .restingHeartRate(h.getRestingHeartRate()).hrvAvg(h.getHrvAvg())
                .stressAvg(h.getStressAvg()).bodyBatteryMin(h.getBodyBatteryMin())
                .bodyBatteryMax(h.getBodyBatteryMax()).steps(h.getSteps())
                .caloriesTotal(h.getCaloriesTotal()).weightKg(h.getWeightKg()).build();
    }

    private SleepDto toSleepDto(GarminSleepSession s) {
        return SleepDto.builder()
                .id(s.getId()).sleepDate(s.getSleepDate())
                .startTime(s.getStartTime()).endTime(s.getEndTime())
                .totalSleepSeconds(s.getTotalSleepSeconds()).deepSleepSeconds(s.getDeepSleepSeconds())
                .lightSleepSeconds(s.getLightSleepSeconds()).remSleepSeconds(s.getRemSleepSeconds())
                .awakeSeconds(s.getAwakeSeconds()).sleepScore(s.getSleepScore()).build();
    }

    private ActivityDto toActivityDto(Activity a) {
        return ActivityDto.builder()
                .id(a.getId()).externalActivityId(a.getExternalActivityId()).sourceType(a.getSourceType())
                .activityType(a.getActivityType()).activityName(a.getActivityName())
                .startTime(a.getStartTime()).durationSeconds(a.getDurationSeconds())
                .distanceMeters(a.getDistanceMeters()).averagePaceSeconds(a.getAveragePaceSeconds())
                .averageHeartRate(a.getAverageHeartRate()).maxHeartRate(a.getMaxHeartRate())
                .calories(a.getCalories()).elevationGainMeters(a.getElevationGainMeters()).build();
    }

    private InsightDto toInsightDto(Insight i) {
        return InsightDto.builder()
                .id(i.getId()).category(i.getCategory()).title(i.getTitle())
                .summary(i.getSummary()).confidence(i.getConfidence())
                .modelProvider(i.getModelProvider()).modelName(i.getModelName())
                .feedbackStatus(i.getFeedbackStatus()).isSaved(i.getIsSaved())
                .createdAt(i.getCreatedAt()).build();
    }
}
