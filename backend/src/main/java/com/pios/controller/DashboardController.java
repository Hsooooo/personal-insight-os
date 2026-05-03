package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.DashboardSummaryDto;
import com.pios.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryDto> summary(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(dashboardService.getSummary(userId));
    }
}
