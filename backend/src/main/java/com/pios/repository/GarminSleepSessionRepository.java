package com.pios.repository;

import com.pios.domain.GarminSleepSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GarminSleepSessionRepository extends JpaRepository<GarminSleepSession, Long> {
    List<GarminSleepSession> findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(
            Long userId, LocalDate start, LocalDate end);

    Optional<GarminSleepSession> findByUserIdAndSleepDate(Long userId, LocalDate sleepDate);

    @Query("SELECT s FROM GarminSleepSession s WHERE s.user.id = :userId ORDER BY s.sleepDate DESC LIMIT 1")
    Optional<GarminSleepSession> findLatestByUserId(@Param("userId") Long userId);
}
