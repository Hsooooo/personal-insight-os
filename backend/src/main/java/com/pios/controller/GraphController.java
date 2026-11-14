package com.pios.controller;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.pios.dto.ApiResponse;
import com.pios.dto.GraphDataDto;
import com.pios.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping
    public ApiResponse<GraphDataDto> getGraph(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") @Min(0) @Max(3650) int days,
            @RequestParam(defaultValue = "all") @Size(max = 30) String view,
            @RequestParam(required = false) @Size(max = 30) String raceCategory) {
        return ApiResponse.ok(graphService.getGraph(userId, days, view, raceCategory));
    }
}
