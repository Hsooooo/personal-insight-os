package com.pios.service;

import com.pios.domain.Goal;
import com.pios.domain.User;
import com.pios.dto.GoalDto;
import com.pios.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepo;

    public List<GoalDto> getGoals(Long userId) {
        return goalRepo.findByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public GoalDto createGoal(Long userId, GoalDto dto) {
        Goal goal = Goal.builder()
                .user(User.builder().id(userId).build())
                .title(dto.getTitle())
                .goalType(dto.getGoalType())
                .description(dto.getDescription())
                .targetValue(dto.getTargetValue())
                .targetUnit(dto.getTargetUnit())
                .startDate(dto.getStartDate())
                .targetDate(dto.getTargetDate())
                .status("ACTIVE")
                .build();
        return toDto(goalRepo.save(goal));
    }

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
        goal.setTitle(dto.getTitle());
        goal.setDescription(dto.getDescription());
        goal.setTargetValue(dto.getTargetValue());
        goal.setTargetUnit(dto.getTargetUnit());
        goal.setStartDate(dto.getStartDate());
        goal.setTargetDate(dto.getTargetDate());
        goal.setStatus(dto.getStatus());
        return toDto(goalRepo.save(goal));
    }

    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        var goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        if (!goal.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        goalRepo.delete(goal);
    }

    private GoalDto toDto(Goal g) {
        return GoalDto.builder()
                .id(g.getId()).title(g.getTitle()).goalType(g.getGoalType())
                .description(g.getDescription()).targetValue(g.getTargetValue())
                .targetUnit(g.getTargetUnit()).startDate(g.getStartDate())
                .targetDate(g.getTargetDate()).status(g.getStatus())
                .build();
    }
}
