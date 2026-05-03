package com.pios.service;

import com.pios.domain.*;
import com.pios.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MockDataService {

    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final GraphProjectorService graphProjector;

    private final Random random = new Random();

    @Transactional
    public void generateMockData(Long userId) {
        User user = User.builder().id(userId).build();
        LocalDate today = LocalDate.now();

        // Generate 30 days of health metrics, sleep, and some activities
        for (int i = 0; i < 30; i++) {
            LocalDate date = today.minusDays(i);
            generateHealthMetric(user, date);
            generateSleep(user, date);
        }

        // Generate 10 activities
        for (int i = 0; i < 10; i++) {
            generateActivity(user, today.minusDays(i * 3));
        }

        // Project to graph
        graphProjector.projectUserData(userId);
    }

    private void generateHealthMetric(User user, LocalDate date) {
        GarminDailyHealthMetric metric = GarminDailyHealthMetric.builder()
                .user(user)
                .metricDate(date)
                .restingHeartRate(55 + random.nextInt(20))
                .hrvAvg(BigDecimal.valueOf(40 + random.nextInt(30)))
                .stressAvg(BigDecimal.valueOf(20 + random.nextInt(50)))
                .bodyBatteryMin(20 + random.nextInt(40))
                .bodyBatteryMax(60 + random.nextInt(40))
                .steps(5000 + random.nextInt(10000))
                .caloriesTotal(1800 + random.nextInt(800))
                .rawPayload(new HashMap<>())
                .build();
        healthRepo.save(metric);
    }

    private void generateSleep(User user, LocalDate date) {
        int totalSleep = 6 * 3600 + random.nextInt(3 * 3600);
        GarminSleepSession sleep = GarminSleepSession.builder()
                .user(user)
                .sleepDate(date)
                .startTime(date.atTime(23, 0).atZone(java.time.ZoneId.systemDefault()).toInstant())
                .endTime(date.plusDays(1).atTime(6 + random.nextInt(3), 0).atZone(java.time.ZoneId.systemDefault()).toInstant())
                .totalSleepSeconds(totalSleep)
                .deepSleepSeconds(totalSleep / 5 + random.nextInt(3600))
                .lightSleepSeconds(totalSleep / 2 + random.nextInt(1800))
                .remSleepSeconds(totalSleep / 4 + random.nextInt(1800))
                .awakeSeconds(random.nextInt(1800))
                .sleepScore(60 + random.nextInt(40))
                .rawPayload(new HashMap<>())
                .build();
        sleepRepo.save(sleep);
    }

    private void generateActivity(User user, LocalDate date) {
        int duration = 1800 + random.nextInt(5400);
        BigDecimal distance = BigDecimal.valueOf(3 + random.nextDouble() * 12);
        Activity activity = Activity.builder()
                .user(user)
                .externalActivityId(UUID.randomUUID().toString())
                .sourceType("GARMIN")
                .activityType(random.nextBoolean() ? "RUNNING" : "CYCLING")
                .activityName(random.nextBoolean() ? "Morning Run" : "Evening Ride")
                .startTime(date.atTime(6 + random.nextInt(12), random.nextInt(60)))
                .durationSeconds(duration)
                .distanceMeters(distance.multiply(BigDecimal.valueOf(1000)))
                .averagePaceSeconds(BigDecimal.valueOf(duration / distance.doubleValue()))
                .averageHeartRate(120 + random.nextInt(50))
                .maxHeartRate(150 + random.nextInt(40))
                .calories(200 + random.nextInt(600))
                .elevationGainMeters(BigDecimal.valueOf(random.nextInt(200)))
                .rawPayload(new HashMap<>())
                .build();
        activityRepo.save(activity);
    }
}
