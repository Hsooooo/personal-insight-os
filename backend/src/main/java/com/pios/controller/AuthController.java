package com.pios.controller;

import com.pios.dto.ApiKeyRequest;
import com.pios.dto.ApiKeyResponse;
import com.pios.dto.ApiResponse;
import com.pios.dto.AuthRequest;
import com.pios.dto.AuthResponse;
import com.pios.dto.UserDto;
import com.pios.service.ApiKeyService;
import com.pios.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final long REFRESH_MAX_AGE_SECONDS = 604800; // 7 days

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
        AuthResponse result = authService.register(request);
        addRefreshCookie(response, result.getRefreshToken());
        return ApiResponse.ok(stripRefreshToken(result));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
        AuthResponse result = authService.login(request);
        addRefreshCookie(response, result.getRefreshToken());
        return ApiResponse.ok(stripRefreshToken(result));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                             HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is missing");
        }
        AuthResponse result = authService.refresh(refreshToken);
        addRefreshCookie(response, result.getRefreshToken());
        return ApiResponse.ok(stripRefreshToken(result));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        authService.logout(userId);
        clearRefreshCookie(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(authService.me(userId));
    }

    // API Keys
    @GetMapping("/api-keys")
    public ApiResponse<List<ApiKeyResponse>> listApiKeys(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(apiKeyService.listKeys(userId));
    }

    @PostMapping("/api-keys")
    public ApiResponse<ApiKeyResponse> createApiKey(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody ApiKeyRequest request) {
        return ApiResponse.ok(apiKeyService.createKey(userId, request.getName()));
    }

    @DeleteMapping("/api-keys/{keyId}")
    public ApiResponse<Void> deleteApiKey(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long keyId) {
        apiKeyService.deleteKey(userId, keyId);
        return ApiResponse.ok(null);
    }

    private AuthResponse stripRefreshToken(AuthResponse result) {
        return AuthResponse.builder()
                .token(result.getToken())
                .user(result.getUser())
                .build();
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(REFRESH_MAX_AGE_SECONDS)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
