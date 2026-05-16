package com.pios.service;

import com.pios.domain.Activity;
import com.pios.domain.Exercise;
import com.pios.domain.GarminActivityLap;
import com.pios.domain.User;
import com.pios.dto.ActivityDto;
import com.pios.dto.ActivityFilterRequest;
import com.pios.dto.GarminActivityLapDto;
import com.pios.dto.WeightTrainingRequest;
import com.pios.repository.ActivityRepository;
import com.pios.repository.ExerciseRepository;
import com.pios.repository.GarminActivityLapRepository;
import com.pios.spec.ActivitySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepo;
    private final ExerciseRepository exerciseRepo;
    private final GarminActivityLapRepository lapRepo;
    private final GraphProjectorService graphProjectorService;

    public Page<ActivityDto> getActivities(Long userId, ActivityFilterRequest filter, Pageable pageable) {
        Pageable effectivePageable = buildPageable(filter, pageable);
        return activityRepo.findAll(
                ActivitySpecification.withFilter(userId, filter),
                effectivePageable
        ).map(this::toDto);
    }

    public ActivityDto getActivity(Long userId, Long activityId) {
        var activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        if (!activity.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        return toDto(activity);
    }

    @Transactional
    public ActivityDto updateUserTag(Long userId, Long activityId, String userTag) {
        var activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        if (!activity.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        String normalizedTag = userTag != null && !userTag.isBlank() ? userTag.trim() : null;
        activity.setUserTag(normalizedTag);
        var saved = activityRepo.save(activity);

        // Sync to Neo4j
        graphProjectorService.updateActivityTag(userId, activityId, normalizedTag);

        return toDto(saved);
    }

    public List<String> getExerciseNames(Long userId) {
        return exerciseRepo.findByUserIdOrderByNameAsc(userId).stream()
                .map(Exercise::getName)
                .toList();
    }

    @Transactional
    public ActivityDto createWeightTraining(Long userId, WeightTrainingRequest request) {
        // Calculate total volume
        BigDecimal totalVolume = BigDecimal.ZERO;
        int totalSets = 0;
        int totalReps = 0;

        List<Map<String, Object>> exerciseMaps = new java.util.ArrayList<>();
        if (request.getExercises() != null) {
            for (WeightTrainingRequest.ExerciseRequest ex : request.getExercises()) {
                List<Map<String, Object>> setMaps = new java.util.ArrayList<>();
                if (ex.getSets() != null) {
                    for (WeightTrainingRequest.SetRequest s : ex.getSets()) {
                        Map<String, Object> setMap = new HashMap<>();
                        setMap.put("reps", s.getReps());
                        setMap.put("weightKg", s.getWeightKg());
                        setMap.put("durationSeconds", s.getDurationSeconds());
                        setMaps.add(setMap);

                        totalSets++;
                        if (s.getReps() != null) { totalReps += s.getReps(); }
                        if (s.getWeightKg() != null && s.getReps() != null) {
                            totalVolume = totalVolume.add(s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
                        }
                    }
                }
                Map<String, Object> exMap = new HashMap<>();
                exMap.put("name", ex.getName());
                exMap.put("sets", setMaps);
                exerciseMaps.add(exMap);
            }
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("bodyPart", request.getBodyPart());
        detail.put("exercises", exerciseMaps);
        detail.put("totalVolumeKg", totalVolume);
        detail.put("totalSets", totalSets);
        detail.put("totalReps", totalReps);
        if (request.getNotes() != null) {
            detail.put("notes", request.getNotes());
        }

        Activity activity = Activity.builder()
                .user(User.builder().id(userId).build())
                .sourceType("MANUAL")
                .activityType("WEIGHT_TRAINING")
                .activityName(request.getActivityName())
                .startTime(request.getStartTime())
                .durationSeconds(request.getDurationSeconds())
                .averageHeartRate(request.getAverageHeartRate())
                .calories(request.getCalories())
                .weightTrainingDetail(detail)
                .build();

        Activity saved = activityRepo.save(activity);

        // Upsert exercises
        upsertExercises(userId, request);

        // Project to Neo4j graph
        graphProjectorService.projectActivity(userId, saved);

        return toDto(saved);
    }

    @Transactional
    public ActivityDto updateWeightTraining(Long userId, Long activityId, WeightTrainingRequest request) {
        Activity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        if (!activity.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        if (!"MANUAL".equals(activity.getSourceType())) {
            throw new IllegalArgumentException("Only manually created activities can be updated");
        }

        // Recalculate totals
        BigDecimal totalVolume = BigDecimal.ZERO;
        int totalSets = 0;
        int totalReps = 0;

        List<Map<String, Object>> exerciseMaps = new java.util.ArrayList<>();
        if (request.getExercises() != null) {
            for (WeightTrainingRequest.ExerciseRequest ex : request.getExercises()) {
                List<Map<String, Object>> setMaps = new java.util.ArrayList<>();
                if (ex.getSets() != null) {
                    for (WeightTrainingRequest.SetRequest s : ex.getSets()) {
                        Map<String, Object> setMap = new HashMap<>();
                        setMap.put("reps", s.getReps());
                        setMap.put("weightKg", s.getWeightKg());
                        setMap.put("durationSeconds", s.getDurationSeconds());
                        setMaps.add(setMap);

                        totalSets++;
                        if (s.getReps() != null) { totalReps += s.getReps(); }
                        if (s.getWeightKg() != null && s.getReps() != null) {
                            totalVolume = totalVolume.add(s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
                        }
                    }
                }
                Map<String, Object> exMap = new HashMap<>();
                exMap.put("name", ex.getName());
                exMap.put("sets", setMaps);
                exerciseMaps.add(exMap);
            }
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("bodyPart", request.getBodyPart());
        detail.put("exercises", exerciseMaps);
        detail.put("totalVolumeKg", totalVolume);
        detail.put("totalSets", totalSets);
        detail.put("totalReps", totalReps);
        if (request.getNotes() != null) {
            detail.put("notes", request.getNotes());
        }

        activity.setActivityName(request.getActivityName());
        activity.setStartTime(request.getStartTime());
        activity.setDurationSeconds(request.getDurationSeconds());
        activity.setAverageHeartRate(request.getAverageHeartRate());
        activity.setCalories(request.getCalories());
        activity.setWeightTrainingDetail(detail);

        Activity saved = activityRepo.save(activity);

        // Upsert exercises
        upsertExercises(userId, request);

        graphProjectorService.projectActivity(userId, saved);

        return toDto(saved);
    }

    private void upsertExercises(Long userId, WeightTrainingRequest request) {
        if (request.getExercises() == null) return;
        for (WeightTrainingRequest.ExerciseRequest ex : request.getExercises()) {
            if (ex.getName() == null || ex.getName().isBlank()) continue;
            String trimmed = ex.getName().trim();
            exerciseRepo.findByUserIdAndName(userId, trimmed)
                    .orElseGet(() -> {
                        Exercise e = Exercise.builder()
                                .user(User.builder().id(userId).build())
                                .name(trimmed)
                                .bodyPart(request.getBodyPart())
                                .build();
                        return exerciseRepo.save(e);
                        });
        }
    }

    @Transactional
    public void deleteWeightTraining(Long userId, Long activityId) {
        Activity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        if (!activity.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        if (!"MANUAL".equals(activity.getSourceType())) {
            throw new IllegalArgumentException("Only manually created activities can be deleted");
        }

        // Remove from Neo4j graph
        graphProjectorService.deleteActivity(userId, activityId);

        activityRepo.delete(activity);
    }

    private Pageable buildPageable(ActivityFilterRequest filter, Pageable pageable) {
        if (filter == null) {
            return pageable;
        }
        String sortBy = filter.getSortBy();
        String sortDir = filter.getSortDir();
        if (sortBy == null || sortBy.isBlank()) {
            return pageable;
        }

        String property = switch (sortBy) {
            case "distance" -> "distanceMeters";
            case "duration" -> "durationSeconds";
            case "calories" -> "calories";
            default -> "startTime";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(direction, property));
    }

    public List<GarminActivityLapDto> getActivityLaps(Long userId, Long activityId) {
        var activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        if (!activity.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        return lapRepo.findByActivityIdOrderByLapIndexAsc(activityId).stream()
                .map(this::toLapDto)
                .toList();
    }

    private ActivityDto toDto(Activity a) {
        return ActivityDto.builder()
                .id(a.getId())
                .externalActivityId(a.getExternalActivityId())
                .sourceType(a.getSourceType())
                .activityType(a.getActivityType())
                .activityName(a.getActivityName())
                .startTime(a.getStartTime())
                .durationSeconds(a.getDurationSeconds())
                .distanceMeters(a.getDistanceMeters())
                .averagePaceSeconds(a.getAveragePaceSeconds())
                .averageHeartRate(a.getAverageHeartRate())
                .maxHeartRate(a.getMaxHeartRate())
                .calories(a.getCalories())
                .elevationGainMeters(a.getElevationGainMeters())
                .userTag(a.getUserTag())
                .weightTrainingDetail(a.getWeightTrainingDetail())
                .build();
    }

    private GarminActivityLapDto toLapDto(GarminActivityLap lap) {
        return GarminActivityLapDto.builder()
                .id(lap.getId())
                .lapIndex(lap.getLapIndex())
                .startTime(lap.getStartTime())
                .durationSeconds(lap.getDurationSeconds())
                .distanceMeters(lap.getDistanceMeters())
                .averagePaceSeconds(lap.getAveragePaceSeconds())
                .averageHeartRate(lap.getAverageHeartRate())
                .maxHeartRate(lap.getMaxHeartRate())
                .build();
    }
}
