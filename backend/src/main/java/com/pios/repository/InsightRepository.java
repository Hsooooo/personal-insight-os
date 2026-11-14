package com.pios.repository;

import com.pios.domain.Insight;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InsightRepository extends JpaRepository<Insight, Long> {
    List<Insight> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Insight> findByUserIdAndIsSavedTrueOrderByCreatedAtDesc(Long userId);

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId AND i.category = :category ORDER BY i.createdAt DESC")
    List<Insight> findByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category);

    // ---- 페이징 조회 (정렬은 쿼리에 고정: created_at DESC, id DESC) ----

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId ORDER BY i.createdAt DESC, i.id DESC")
    List<Insight> findPageByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId AND i.category = :category ORDER BY i.createdAt DESC, i.id DESC")
    List<Insight> findPageByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category,
                                              Pageable pageable);

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId AND i.feedbackStatus = :status ORDER BY i.createdAt DESC, i.id DESC")
    List<Insight> findPageByUserIdAndFeedbackStatus(@Param("userId") Long userId, @Param("status") String status,
                                                    Pageable pageable);

    @Query("""
            SELECT i FROM Insight i
            WHERE i.user.id = :userId AND i.category = :category AND i.feedbackStatus = :status
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<Insight> findPageByUserIdAndCategoryAndFeedbackStatus(@Param("userId") Long userId,
                                                               @Param("category") String category,
                                                               @Param("status") String status,
                                                               Pageable pageable);

    /** 대시보드: 특정 카테고리(주간 브리핑)를 제외한 최신 N개 */
    @Query("""
            SELECT i FROM Insight i
            WHERE i.user.id = :userId AND (i.category IS NULL OR i.category <> :excludedCategory)
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<Insight> findRecentExcludingCategory(@Param("userId") Long userId,
                                              @Param("excludedCategory") String excludedCategory,
                                              Pageable pageable);

    @Query("SELECT i FROM Insight i WHERE i.user.id = :userId AND i.feedbackStatus = :status ORDER BY i.createdAt DESC")
    List<Insight> findByUserIdAndFeedbackStatus(@Param("userId") Long userId, @Param("status") String status);

    Optional<Insight> findByIdAndUserId(Long id, Long userId);

    Optional<Insight> findFirstByUserIdAndCategoryOrderByCreatedAtDesc(Long userId, String category);

    @Query("""
            SELECT i FROM Insight i
            WHERE i.user.id = :userId
              AND i.category = :category
              AND i.createdAt >= :start
              AND i.createdAt < :end
            ORDER BY i.createdAt DESC
            """)
    List<Insight> findByUserIdAndCategoryAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
