package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityDto {
    private Long id;
    private String externalActivityId;
    private String sourceType;
    private String activityType;
    private String activityName;
    private LocalDateTime startTime;
    private Integer durationSeconds;
    private BigDecimal distanceMeters;
    private BigDecimal averagePaceSeconds;
    private Integer averageHeartRate;
    private Integer maxHeartRate;
    private Integer calories;
    private BigDecimal elevationGainMeters;
    private String userTag;
    private Map<String, Object> weightTrainingDetail;
}
