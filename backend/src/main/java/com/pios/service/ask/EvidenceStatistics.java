package com.pios.service.ask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceStatistics {
    private List<MetricStatistic> healthMetrics;
    private List<MetricStatistic> sleepMetrics;
    private List<MetricStatistic> activityMetrics;
}
