package com.pios.service;

import com.pios.domain.Goal;
import com.pios.domain.User;
import com.pios.dto.GoalDto;
import com.pios.repository.GoalRepository;
import com.pios.service.goal.GoalBlockerAnalyzer;
import com.pios.service.goal.GoalProgressCalculator;
import com.pios.service.goal.GoalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepo;
    private final GoalProgressCalculator progressCalculator;
    private final GoalBlockerAnalyzer blockerAnalyzer;
    private final GraphProjectorService graphProjector;

    @Transactional(readOnly = true)
    public List<GoalDto> getGoals(Long userId) {
        return goalRepo.findByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public GoalDto createGoal(Long userId, GoalDto dto) {
        String unit = dto.getTargetUnit();
        if (unit == null || unit.isBlank()) {
            unit = GoalType.from(dto.getGoalType()).map(GoalType::getDefaultUnit).orElse(null);
        }
        Goal goal = Goal.builder()
                .user(User.builder().id(userId).build())
                .title(dto.getTitle())
                .goalType(dto.getGoalType())
                .description(dto.getDescription())
                .targetValue(dto.getTargetValue())
                .targetUnit(unit)
                .startDate(dto.getStartDate())
                .targetDate(dto.getTargetDate())
                .status("ACTIVE")
                .build();
        Goal saved = goalRepo.save(goal);
        graphProjector.projectGoal(userId, saved);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public GoalDto getGoal(Long userId, Long goalId) {
        var goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        if (!goal.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        return toDto(goal);
    }

    @Transactional
    public GoalDto updateGoal(Long userId, Long goalId, GoalDto dto) {
        var goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        if (!goal.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        if (dto.getTitle() != null) goal.setTitle(dto.getTitle());
        if (dto.getDescription() != null) goal.setDescription(dto.getDescription());
        if (dto.getGoalType() != null) goal.setGoalType(dto.getGoalType());
        if (dto.getTargetValue() != null) goal.setTargetValue(dto.getTargetValue());
        if (dto.getTargetUnit() != null) goal.setTargetUnit(dto.getTargetUnit());
        if (dto.getStartDate() != null) goal.setStartDate(dto.getStartDate());
        if (dto.getTargetDate() != null) goal.setTargetDate(dto.getTargetDate());
        if (dto.getStatus() != null) goal.setStatus(dto.getStatus());
        Goal saved = goalRepo.save(goal);
        graphProjector.projectGoal(userId, saved);
        return toDto(saved);
    }

    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        var goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        if (!goal.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        goalRepo.delete(goal);
        graphProjector.deleteGoal(userId, goalId);
    }

    private GoalDto toDto(Goal g) {
        var progress = progressCalculator.calculate(g);
        return GoalDto.builder()
                .id(g.getId()).title(g.getTitle()).goalType(g.getGoalType())
                .description(g.getDescription()).targetValue(g.getTargetValue())
                .targetUnit(g.getTargetUnit()).startDate(g.getStartDate())
                .targetDate(g.getTargetDate()).status(g.getStatus())
                .progressSupported(progress.isSupported())
                .currentValue(progress.getCurrentValue())
                .progressPercent(progress.getProgressPercent())
                .paceStatus(progress.getPaceStatus())
                .projectedDate(progress.getProjectedDate())
                .warning(progress.getWarning())
                .blockers(blockerAnalyzer.analyze(g))
                .build();
    }
}
