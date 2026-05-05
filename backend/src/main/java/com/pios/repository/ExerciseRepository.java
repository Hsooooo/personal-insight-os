package com.pios.repository;

import com.pios.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findByUserIdOrderByNameAsc(Long userId);
    Optional<Exercise> findByUserIdAndName(Long userId, String name);
}
