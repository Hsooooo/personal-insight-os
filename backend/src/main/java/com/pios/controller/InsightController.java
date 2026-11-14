package com.pios.controller;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.pios.dto.ApiResponse;
import com.pios.dto.FeedbackRequest;
import com.pios.dto.InsightDto;
import com.pios.service.InsightService;
import com.pios.service.alert.AnomalyAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;
    private final AnomalyAlertService anomalyAlertService;

    @PostMapping("/anomaly-alerts/detect")
    public ApiResponse<List<InsightDto>> detectAnomalyAlerts(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(anomalyAlertService.detectAndAlert(userId));
    }

    @GetMapping
    public ApiResponse<List<InsightDto>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(required = false) @Size(max = 30) String feedbackStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + InsightService.DEFAULT_PAGE_SIZE) @Min(1) @Max(InsightService.MAX_PAGE_SIZE) int size) {
        return ApiResponse.ok(insightService.getInsights(userId, category, feedbackStatus, page, size));
    }

    @GetMapping("/saved")
    public ApiResponse<List<InsightDto>> saved(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(insightService.getSavedInsights(userId));
    }

    @GetMapping("/{insightId}")
    public ApiResponse<InsightDto> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long insightId) {
        return ApiResponse.ok(insightService.getInsight(userId, insightId));
    }

    @PostMapping("/{insightId}/save")
    public ApiResponse<InsightDto> save(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long insightId) {
        return ApiResponse.ok(insightService.saveInsight(userId, insightId));
    }

    @PostMapping("/{insightId}/feedback")
    public ApiResponse<InsightDto> feedback(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long insightId,
            @Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(insightService.feedback(userId, insightId, request));
    }

    @DeleteMapping("/{insightId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long insightId) {
        insightService.deleteInsight(userId, insightId);
        return ApiResponse.ok("Deleted", null);
    }
}
