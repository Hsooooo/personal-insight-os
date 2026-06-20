package com.pios.service.ask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricStatistic {
    private String metric;
    private String label;
    private BigDecimal currentValue;
    private BigDecimal baselineValue;
    private BigDecimal changeRate;
    private String unit;
    private int currentDays;
    private int baselineDays;
    private boolean reliable;
    private Long sourceId;
    private String sourceDate;
}
