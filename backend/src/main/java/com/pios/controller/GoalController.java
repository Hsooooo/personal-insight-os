package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.GoalDto;
import com.pios.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ApiResponse<List<GoalDto>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(goalService.getGoals(userId));
    }

    @PostMapping
    public ApiResponse<GoalDto> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody GoalDto dto) {
        return ApiResponse.ok(goalService.createGoal(userId, dto));
    }

    @GetMapping("/{goalId}")
    public ApiResponse<GoalDto> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalId) {
        return ApiResponse.ok(goalService.getGoal(userId, goalId));
    }

    @PatchMapping("/{goalId}")
    public ApiResponse<GoalDto> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalId,
            @RequestBody GoalDto dto) {
        return ApiResponse.ok(goalService.updateGoal(userId, goalId, dto));
    }

    @DeleteMapping("/{goalId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalId) {
        goalService.deleteGoal(userId, goalId);
        return ApiResponse.ok("Deleted", null);
    }
}
