package com.pios.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthGrantResponse {
    private Long id;
    private String clientId;
    private String clientName;
    private List<String> scopes;
    private Instant createdAt;
    private Instant lastUsedAt;
}
