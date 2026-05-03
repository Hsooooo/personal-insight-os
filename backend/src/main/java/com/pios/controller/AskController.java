package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.AskRequest;
import com.pios.dto.AskResponse;
import com.pios.service.AskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ask")
@RequiredArgsConstructor
public class AskController {

    private final AskService askService;

    @PostMapping
    public ApiResponse<AskResponse> ask(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AskRequest request) {
        return ApiResponse.ok(askService.ask(userId, request));
    }
}
