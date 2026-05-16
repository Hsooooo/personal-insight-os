package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GarminActivityLapDto {

    private Long id;
    private Integer lapIndex;
    private Instant startTime;
    private Integer durationSeconds;
    private BigDecimal distanceMeters;
    private BigDecimal averagePaceSeconds;
    private Integer averageHeartRate;
    private Integer maxHeartRate;
}
