package com.pios.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ActivityFilterRequest {
    @Size(max = 50)
    private String activityType;
    @Size(max = 100)
    private String userTag;
    @Size(max = 255)
    private String activityName;
    private LocalDate startTimeFrom;
    private LocalDate startTimeTo;
    private BigDecimal minDistance;
    private BigDecimal maxDistance;
    @Pattern(regexp = "startTime|distance|duration|calories", message = "must be one of startTime, distance, duration, calories")
    private String sortBy;
    @Pattern(regexp = "(?i)asc|desc", message = "must be asc or desc")
    private String sortDir;
}
