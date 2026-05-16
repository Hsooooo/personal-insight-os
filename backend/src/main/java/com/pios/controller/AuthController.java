package com.pios.controller;

import com.pios.dto.ApiKeyRequest;
import com.pios.dto.ApiKeyResponse;
import com.pios.dto.ApiResponse;
import com.pios.dto.AuthRequest;
import com.pios.dto.AuthResponse;
import com.pios.dto.UserDto;
import com.pios.service.ApiKeyService;
import com.pios.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.ok(authService.login(request));
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
}
