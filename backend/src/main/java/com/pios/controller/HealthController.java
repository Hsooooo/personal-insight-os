package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.HealthMetricDto;
import com.pios.dto.SleepDto;
import com.pios.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/metrics")
    public ApiResponse<List<HealthMetricDto>> metrics(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        validateRange(start, end);
        return ApiResponse.ok(healthService.getMetrics(userId, start, end));
    }

    @GetMapping("/sleep")
    public ApiResponse<List<SleepDto>> sleep(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        validateRange(start, end);
        return ApiResponse.ok(healthService.getSleep(userId, start, end));
    }

    private static final long MAX_RANGE_DAYS = 3 * 366;

    private static void validateRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must be on or after start");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }
}
