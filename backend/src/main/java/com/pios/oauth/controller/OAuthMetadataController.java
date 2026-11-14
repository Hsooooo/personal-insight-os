package com.pios.oauth.controller;

import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RFC 8414 Authorization Server Metadata (served at the root, not under /api). */
@RestController
@RequiredArgsConstructor
public class OAuthMetadataController {

    private final OAuthProperties properties;

    @GetMapping({"/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/**"})
    public ResponseEntity<Map<String, Object>> metadata() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", properties.getIssuer());
        body.put("authorization_endpoint", properties.endpoint("/api/oauth/authorize"));
        body.put("token_endpoint", properties.endpoint("/api/oauth/token"));
        body.put("registration_endpoint", properties.endpoint("/api/oauth/register"));
        body.put("revocation_endpoint", properties.endpoint("/api/oauth/revoke"));
        body.put("response_types_supported", List.of("code"));
        body.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        body.put("code_challenge_methods_supported", List.of("S256"));
        body.put("token_endpoint_auth_methods_supported", List.of("none"));
        body.put("revocation_endpoint_auth_methods_supported", List.of("none"));
        body.put("scopes_supported", OAuthScopes.SUPPORTED);
        body.put("client_id_metadata_document_supported", true);
        body.put("authorization_response_iss_parameter_supported", true);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(body);
    }
}
