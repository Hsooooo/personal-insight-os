package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.InsightDto;
import com.pios.service.briefing.WeeklyBriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/briefings")
@RequiredArgsConstructor
public class BriefingController {

    private final WeeklyBriefingService weeklyBriefingService;

    @GetMapping("/latest")
    public ApiResponse<InsightDto> latest(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(weeklyBriefingService.getLatest(userId));
    }

    @PostMapping("/generate")
    public ApiResponse<InsightDto> generate(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        return ApiResponse.ok(weeklyBriefingService.generate(userId, force));
    }
}
