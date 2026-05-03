package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardSummaryDto {
    private HealthMetricDto latestHealth;
    private SleepDto latestSleep;
    private ActivityDto latestActivity;
    private Long totalActivities;
    private List<HealthMetricDto> last7DaysHealth;
    private List<ActivityDto> last7DaysActivities;
    private List<InsightDto> recentInsights;
    private List<String> suggestedQuestions;
}
