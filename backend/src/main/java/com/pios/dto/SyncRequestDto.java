package com.pios.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SyncRequestDto {
    @Pattern(regexp = "(?i)FULL|INCREMENTAL|MANUAL", message = "must be one of FULL, INCREMENTAL, MANUAL")
    private String syncType;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
