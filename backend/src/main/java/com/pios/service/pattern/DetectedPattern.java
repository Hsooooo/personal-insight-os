package com.pios.service.pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectedPattern {
    private String code;
    private String severity; // HIGH | MED | LOW
    private String metric;
    private BigDecimal currentValue;
    private BigDecimal baselineValue;
    private BigDecimal changeRate;
    private String unit;
    private String humanHint;
}
