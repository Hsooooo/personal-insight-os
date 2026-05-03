package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProviderConnectionDto {
    private Long id;
    private String providerType;
    private String connectionStatus;
    private Instant lastSyncedAt;
    private java.util.Map<String, Object> syncConfig;
    private Long dataCount;
    private Instant createdAt;
}
