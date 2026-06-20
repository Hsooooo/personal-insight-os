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
public class EvidenceData {
    private String metric;
    private BigDecimal currentValue;
    private BigDecimal baselineValue;
    private BigDecimal changeRate;
    private String unit;
    private String date;
    private String route;
}
