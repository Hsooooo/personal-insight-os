package com.pios.controller;

import com.pios.dto.ActivityDto;
import com.pios.dto.ActivityFilterRequest;
import com.pios.dto.ApiResponse;
import com.pios.dto.GarminActivityLapDto;
import com.pios.dto.WeightTrainingRequest;
import com.pios.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/exercises")
    public ApiResponse<List<String>> getExerciseNames(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(activityService.getExerciseNames(userId));
    }

    @GetMapping
    public ApiResponse<Page<ActivityDto>> list(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable,
            ActivityFilterRequest filter) {
        return ApiResponse.ok(activityService.getActivities(userId, filter, pageable));
    }

    @GetMapping("/{activityId}")
    public ApiResponse<ActivityDto> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId) {
        return ApiResponse.ok(activityService.getActivity(userId, activityId));
    }

    @GetMapping("/{activityId}/laps")
    public ApiResponse<List<GarminActivityLapDto>> laps(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId) {
        return ApiResponse.ok(activityService.getActivityLaps(userId, activityId));
    }

    @PostMapping("/weight")
    public ApiResponse<ActivityDto> createWeightTraining(
            @AuthenticationPrincipal Long userId,
            @RequestBody WeightTrainingRequest request) {
        return ApiResponse.ok(activityService.createWeightTraining(userId, request));
    }

    @PatchMapping("/{activityId}")
    public ApiResponse<ActivityDto> updateWeightTraining(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId,
            @RequestBody WeightTrainingRequest request) {
        return ApiResponse.ok(activityService.updateWeightTraining(userId, activityId, request));
    }

    @DeleteMapping("/{activityId}")
    public ApiResponse<Void> deleteWeightTraining(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId) {
        activityService.deleteWeightTraining(userId, activityId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{activityId}/tag")
    public ApiResponse<ActivityDto> updateTag(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId,
            @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(activityService.updateUserTag(userId, activityId, body.get("userTag")));
    }
}
