package com.pios.repository;

import com.pios.domain.GarminActivityLap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GarminActivityLapRepository extends JpaRepository<GarminActivityLap, Long> {
    List<GarminActivityLap> findByActivityIdIn(List<Long> activityIds);
}
