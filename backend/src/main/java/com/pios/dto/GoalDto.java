package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GoalDto {
    private Long id;
    private String title;
    private String goalType;
    private String description;
    private BigDecimal targetValue;
    private String targetUnit;
    private LocalDate startDate;
    private LocalDate targetDate;
    private String status;

    /** 자동 추적 가능 여부 */
    private Boolean progressSupported;
    private BigDecimal currentValue;
    private BigDecimal progressPercent;
    private String paceStatus;
    private LocalDate projectedDate;
    private String warning;
}
