package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SyncRequestDto {
    private String syncType;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
