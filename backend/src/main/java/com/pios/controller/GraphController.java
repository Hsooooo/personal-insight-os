package com.pios.controller;

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
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(graphService.getGraph(userId, days));
    }
}
