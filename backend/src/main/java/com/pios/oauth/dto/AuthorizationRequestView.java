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
public class AuthorizationRequestView {
    private String requestId;
    private String clientName;
    private String clientId;
    private String redirectUri;
    private String redirectHost;
    private List<String> scopes;
    private Instant expiresAt;
}
