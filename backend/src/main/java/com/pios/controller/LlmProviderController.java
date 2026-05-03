package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.LlmProviderDto;
import com.pios.dto.LlmProviderRequest;
import com.pios.service.LlmProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings/llm-providers")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderService llmProviderService;

    @GetMapping
    public ApiResponse<List<LlmProviderDto>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(llmProviderService.getProviders(userId));
    }

    @PostMapping
    public ApiResponse<LlmProviderDto> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody LlmProviderRequest request) {
        return ApiResponse.ok(llmProviderService.createProvider(userId, request));
    }

    @PatchMapping("/{providerId}")
    public ApiResponse<LlmProviderDto> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long providerId,
            @Valid @RequestBody LlmProviderRequest request) {
        return ApiResponse.ok(llmProviderService.updateProvider(userId, providerId, request));
    }

    @DeleteMapping("/{providerId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long providerId) {
        llmProviderService.deleteProvider(userId, providerId);
        return ApiResponse.ok("Deleted", null);
    }
}
