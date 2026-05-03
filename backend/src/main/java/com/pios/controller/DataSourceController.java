package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.ProviderConnectionDto;
import com.pios.dto.SyncLogDto;
import com.pios.dto.SyncRequestDto;
import com.pios.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data-sources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @GetMapping
    public ApiResponse<List<ProviderConnectionDto>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(dataSourceService.getConnections(userId));
    }

    @PostMapping("/garmin/connect")
    public ApiResponse<ProviderConnectionDto> connectGarmin(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(dataSourceService.connectGarmin(userId, body.get("email"), body.get("password")));
    }

    @PostMapping("/garmin/sync")
    public ApiResponse<ProviderConnectionDto> syncGarmin(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) SyncRequestDto request) {
        if (request == null) {
            request = new SyncRequestDto();
            request.setSyncType("INCREMENTAL");
        }
        return ApiResponse.ok(dataSourceService.syncGarmin(
                userId, request.getSyncType(), request.getDateFrom(), request.getDateTo()));
    }

    @GetMapping("/garmin/sync-logs")
    public ApiResponse<List<SyncLogDto>> getSyncLogs(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(dataSourceService.getSyncLogs(userId));
    }

    @PostMapping("/garmin/mock")
    public ApiResponse<ProviderConnectionDto> generateMockData(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(dataSourceService.generateMockData(userId));
    }

    @DeleteMapping("/garmin")
    public ApiResponse<Void> disconnectGarmin(@AuthenticationPrincipal Long userId) {
        dataSourceService.disconnectGarmin(userId);
        return ApiResponse.ok("Garmin disconnected", null);
    }
}
