package com.pios.repository;

import com.pios.domain.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {
    Page<Activity> findByUserIdOrderByStartTimeDesc(Long userId, Pageable pageable);

    List<Activity> findByUserIdAndUserTagOrderByStartTimeDesc(Long userId, String userTag);

    @Query("SELECT a FROM Activity a WHERE a.user.id = :userId AND a.startTime >= :since ORDER BY a.startTime DESC")
    List<Activity> findRecentByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    Optional<Activity> findByUserIdAndExternalActivityId(Long userId, String externalActivityId);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);
}
