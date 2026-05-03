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
}
