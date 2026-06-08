package com.pios.service;

import com.pios.domain.GarminDailyHealthMetric;
import com.pios.domain.GarminSleepSession;
import com.pios.dto.HealthMetricDto;
import com.pios.dto.SleepDto;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;

    public List<HealthMetricDto> getMetrics(Long userId, LocalDate start, LocalDate end) {
        return healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(userId, start, end)
                .stream().map(this::toHealthDto).toList();
    }

    public List<SleepDto> getSleep(Long userId, LocalDate start, LocalDate end) {
        return sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(userId, start, end)
                .stream().map(this::toSleepDto).toList();
    }

    private HealthMetricDto toHealthDto(GarminDailyHealthMetric h) {
        return HealthMetricDto.builder()
                .id(h.getId()).metricDate(h.getMetricDate())
                .restingHeartRate(h.getRestingHeartRate()).hrvAvg(h.getHrvAvg())
                .stressAvg(h.getStressAvg()).bodyBatteryMin(h.getBodyBatteryMin())
                .bodyBatteryMax(h.getBodyBatteryMax()).steps(h.getSteps())
                .caloriesTotal(h.getCaloriesTotal()).weightKg(h.getWeightKg()).build();
    }

    private SleepDto toSleepDto(GarminSleepSession s) {
        return SleepDto.builder()
                .id(s.getId()).sleepDate(s.getSleepDate())
                .startTime(s.getStartTime()).endTime(s.getEndTime())
                .totalSleepSeconds(s.getTotalSleepSeconds()).deepSleepSeconds(s.getDeepSleepSeconds())
                .lightSleepSeconds(s.getLightSleepSeconds()).remSleepSeconds(s.getRemSleepSeconds())
                .awakeSeconds(s.getAwakeSeconds()).sleepScore(s.getSleepScore()).build();
    }
}
