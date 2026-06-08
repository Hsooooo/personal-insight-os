package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HealthMetricDto {
    private Long id;
    private LocalDate metricDate;
    private Integer restingHeartRate;
    private BigDecimal hrvAvg;
    private BigDecimal stressAvg;
    private Integer bodyBatteryMin;
    private Integer bodyBatteryMax;
    private Integer steps;
    private Integer caloriesTotal;
    private BigDecimal weightKg;
}
