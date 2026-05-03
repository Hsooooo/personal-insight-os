package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SyncLogDto {
    private Long id;
    private String providerType;
    private String syncType;
    private String status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer activitiesCount;
    private Integer healthMetricsCount;
    private Integer sleepCount;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
