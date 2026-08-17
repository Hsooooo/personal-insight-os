package com.pios.repository;

import com.pios.domain.GarminDailyRaw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface GarminDailyRawRepository extends JpaRepository<GarminDailyRaw, Long> {
    Optional<GarminDailyRaw> findByUserIdAndMetricDateAndDataType(Long userId, LocalDate metricDate, String dataType);
}
