package com.pios.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WeightTrainingRequest {
    private String activityName;
    private LocalDateTime startTime;
    private Integer durationSeconds;
    private Integer averageHeartRate;
    private Integer calories;
    private String notes;
    private String bodyPart;
    private List<ExerciseRequest> exercises;

    @Data
    public static class ExerciseRequest {
        private String name;
        private List<SetRequest> sets;
    }

    @Data
    public static class SetRequest {
        private Integer reps;
        private BigDecimal weightKg;
    }
}
