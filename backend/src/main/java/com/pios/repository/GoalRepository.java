package com.pios.repository;

import java.util.Optional;
import com.pios.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserId(Long userId);
    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
