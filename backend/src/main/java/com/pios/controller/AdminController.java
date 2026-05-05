package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.service.GraphProjectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final GraphProjectorService graphProjector;

    @PostMapping("/backfill")
    public ApiResponse<String> backfill(@AuthenticationPrincipal Long userId) {
        graphProjector.projectUserData(userId);
        return ApiResponse.ok("Backfill completed", "userId=" + userId);
    }
}
