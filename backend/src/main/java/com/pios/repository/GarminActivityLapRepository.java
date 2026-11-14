package com.pios.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pios.domain.Activity;
import com.pios.domain.GarminActivityLap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GarminActivityLapRepository extends JpaRepository<GarminActivityLap, Long> {
    List<GarminActivityLap> findByActivityIdIn(List<Long> activityIds);

    List<GarminActivityLap> findByActivityIdOrderByLapIndexAsc(Long activityId);

    void deleteByActivityId(Long activityId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM GarminActivityLap l WHERE l.activity.id IN :activityIds")
    int deleteByActivityIdIn(@Param("activityIds") java.util.Collection<Long> activityIds);
}
