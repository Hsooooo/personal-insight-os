package com.pios.repository;

import com.pios.domain.Insight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsightRepository extends JpaRepository<Insight, Long> {
    List<Insight> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Insight> findByUserIdAndIsSavedTrueOrderByCreatedAtDesc(Long userId);

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId AND i.category = :category ORDER BY i.createdAt DESC")
    List<Insight> findByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category);

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId AND i.feedbackStatus = :status ORDER BY i.createdAt DESC")
    List<Insight> findByUserIdAndFeedbackStatus(@Param("userId") Long userId, @Param("status") String status);

    Optional<Insight> findByIdAndUserId(Long id, Long userId);
}
