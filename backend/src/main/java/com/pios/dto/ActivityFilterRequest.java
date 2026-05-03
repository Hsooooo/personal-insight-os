package com.pios.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ActivityFilterRequest {
    private String activityType;
    private String userTag;
    private String activityName;
    private LocalDate startTimeFrom;
    private LocalDate startTimeTo;
    private BigDecimal minDistance;
    private BigDecimal maxDistance;
    private String sortBy;
    private String sortDir;
}
