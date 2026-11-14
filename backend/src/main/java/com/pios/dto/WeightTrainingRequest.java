package com.pios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WeightTrainingRequest {
    @Size(max = 255)
    private String activityName;
    @NotNull
    private LocalDateTime startTime;
    @PositiveOrZero
    @Max(86400)
    private Integer durationSeconds;
    @Min(20)
    @Max(260)
    private Integer averageHeartRate;
    @PositiveOrZero
    @Max(20000)
    private Integer calories;
    @Size(max = 2000)
    private String notes;
    @Size(max = 50)
    private String bodyPart;
    @Valid
    @Size(max = 50)
    private List<ExerciseRequest> exercises;

    @Data
    public static class ExerciseRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @Valid
        @Size(max = 100)
        private List<SetRequest> sets;
    }

    @Data
    public static class SetRequest {
        @PositiveOrZero
        @Max(10000)
        private Integer reps;
        @DecimalMin("0")
        @DecimalMax("1000")
        private BigDecimal weightKg;
        @PositiveOrZero
        @Max(86400)
        private Integer durationSeconds;
    }
}
