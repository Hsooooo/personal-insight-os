package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskEvidence {
    private String type;
    private String label;
    private String observation;
    private String comparison;
    private BigDecimal currentValue;
    private BigDecimal baselineValue;
    private BigDecimal changeRate;
    private String unit;
    private Long sourceId;
    private String sourceDate;
    private String route;
}
