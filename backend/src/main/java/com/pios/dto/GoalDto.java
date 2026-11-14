package com.pios.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GoalDto {
    private Long id;
    @NotBlank(groups = ValidationGroups.Create.class)
    @Size(max = 255)
    private String title;
    @NotBlank(groups = ValidationGroups.Create.class)
    @Size(max = 50)
    private String goalType;
    @Size(max = 2000)
    private String description;
    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    private BigDecimal targetValue;
    @Size(max = 50)
    private String targetUnit;
    private LocalDate startDate;
    private LocalDate targetDate;
    @Pattern(regexp = "[A-Z_]{1,30}", message = "must be an upper-case status code (e.g. ACTIVE)")
    private String status;

    /** 자동 추적 가능 여부 */
    private Boolean progressSupported;
    private BigDecimal currentValue;
    private BigDecimal progressPercent;
    private String paceStatus;
    private LocalDate projectedDate;
    private String warning;

    /** 이번 주 진행을 막는 상위 1~2 요인 (규칙 기반) */
    private List<String> blockers;
}
