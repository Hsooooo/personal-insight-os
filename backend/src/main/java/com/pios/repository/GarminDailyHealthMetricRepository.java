package com.pios.repository;

import com.pios.domain.GarminDailyHealthMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GarminDailyHealthMetricRepository extends JpaRepository<GarminDailyHealthMetric, Long> {
    List<GarminDailyHealthMetric> findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
            Long userId, LocalDate start, LocalDate end);

    Optional<GarminDailyHealthMetric> findByUserIdAndMetricDate(Long userId, LocalDate metricDate);

    @Query("SELECT m FROM GarminDailyHealthMetric m WHERE m.user.id = :userId ORDER BY m.metricDate DESC LIMIT 1")
    Optional<GarminDailyHealthMetric> findLatestByUserId(@Param("userId") Long userId);
}
