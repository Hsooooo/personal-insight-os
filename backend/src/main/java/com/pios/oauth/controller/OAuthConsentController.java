package com.pios.oauth.controller;

import com.pios.dto.ApiResponse;
import com.pios.oauth.OAuthNotFoundException;
import com.pios.oauth.dto.AuthorizationRequestView;
import com.pios.oauth.dto.ConsentDecisionRequest;
import com.pios.oauth.dto.ConsentDecisionResponse;
import com.pios.oauth.dto.OAuthGrantResponse;
import com.pios.oauth.service.OAuthAuthorizationService;
import com.pios.oauth.service.OAuthTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Consent + connected-apps APIs for the web UI (normal user JWT; ROLE_MCP principals are blocked). */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthConsentController {

    private final OAuthAuthorizationService authorizationService;
    private final OAuthTokenService tokenService;

    @GetMapping("/requests/{requestId}")
    public ApiResponse<AuthorizationRequestView> getRequest(@PathVariable String requestId) {
        return ApiResponse.ok(authorizationService.getRequest(requestId));
    }

    @PostMapping("/requests/{requestId}/decision")
    public ApiResponse<ConsentDecisionResponse> decide(@AuthenticationPrincipal Long userId,
                                                       @PathVariable String requestId,
                                                       @Valid @RequestBody ConsentDecisionRequest request) {
        String redirectUrl = authorizationService.decide(requestId, userId, Boolean.TRUE.equals(request.getApprove()));
        return ApiResponse.ok(new ConsentDecisionResponse(redirectUrl));
    }

    @GetMapping("/grants")
    public ApiResponse<List<OAuthGrantResponse>> listGrants(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(tokenService.listGrants(userId));
    }

    @DeleteMapping("/grants/{grantId}")
    public ApiResponse<Void> revokeGrant(@AuthenticationPrincipal Long userId, @PathVariable Long grantId) {
        tokenService.revokeGrant(userId, grantId);
        return ApiResponse.ok(null);
    }

    @ExceptionHandler(OAuthNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(OAuthNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }
}
