package com.pios.service;

import com.pios.domain.ProviderConnection;
import com.pios.domain.User;
import com.pios.domain.enums.SyncType;
import com.pios.dto.ProviderConnectionDto;
import com.pios.dto.SyncLogDto;
import com.pios.repository.ActivityRepository;
import com.pios.repository.ProviderConnectionRepository;
import com.pios.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final ProviderConnectionRepository providerRepo;
    private final ActivityRepository activityRepo;
    private final SyncLogRepository syncLogRepo;
    private final GarminSyncService garminSyncService;
    private final MockDataService mockDataService;

    public List<ProviderConnectionDto> getConnections(Long userId) {
        return providerRepo.findByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ProviderConnectionDto connectGarmin(Long userId, String email, String password) {
        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .orElseGet(() -> ProviderConnection.builder()
                        .user(User.builder().id(userId).build())
                        .providerType("GARMIN")
                        .build());
        conn.setConnectionStatus("CONNECTED");
        conn.setAuthPayload(Map.of("email", email, "password", password));
        java.util.Map<String, Object> syncConfig = new java.util.HashMap<>();
        syncConfig.put("full_sync_from", null);
        syncConfig.put("last_sync_date", null);
        syncConfig.put("sync_range_days", 7);
        syncConfig.put("auto_sync_enabled", true);
        syncConfig.put("auto_sync_cron", "0 3 * * *");
        conn.setSyncConfig(syncConfig);
        conn = providerRepo.save(conn);
        return toDto(conn);
    }

    @Transactional
    public void disconnectGarmin(Long userId) {
        providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .ifPresent(conn -> {
                    conn.setConnectionStatus("DISCONNECTED");
                    providerRepo.save(conn);
                });
    }

    @Transactional
    public ProviderConnectionDto syncGarmin(Long userId, String syncType, LocalDate dateFrom, LocalDate dateTo) {
        SyncType type;
        try {
            type = SyncType.valueOf(syncType != null ? syncType.toUpperCase() : "INCREMENTAL");
        } catch (IllegalArgumentException e) {
            type = SyncType.INCREMENTAL;
        }

        garminSyncService.sync(userId, type, dateFrom, dateTo);

        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .orElseThrow(() -> new IllegalArgumentException("Garmin not connected"));
        return toDto(conn);
    }

    @Transactional
    public ProviderConnectionDto generateMockData(Long userId) {
        mockDataService.generateMockData(userId);

        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .orElseGet(() -> ProviderConnection.builder()
                        .user(User.builder().id(userId).build())
                        .providerType("GARMIN")
                        .build());
        conn.setConnectionStatus("CONNECTED");
        conn.setLastSyncedAt(java.time.Instant.now());
        conn = providerRepo.save(conn);
        return toDto(conn);
    }

    public List<SyncLogDto> getSyncLogs(Long userId) {
        return syncLogRepo.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(log -> SyncLogDto.builder()
                        .id(log.getId())
                        .providerType(log.getProviderType())
                        .syncType(log.getSyncType())
                        .status(log.getStatus())
                        .dateFrom(log.getDateFrom())
                        .dateTo(log.getDateTo())
                        .activitiesCount(log.getActivitiesCount())
                        .healthMetricsCount(log.getHealthMetricsCount())
                        .sleepCount(log.getSleepCount())
                        .errorMessage(log.getErrorMessage())
                        .startedAt(log.getStartedAt())
                        .completedAt(log.getCompletedAt())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    private ProviderConnectionDto toDto(ProviderConnection c) {
        long count = activityRepo.countByUserId(c.getUser().getId());
        return ProviderConnectionDto.builder()
                .id(c.getId())
                .providerType(c.getProviderType())
                .connectionStatus(c.getConnectionStatus())
                .lastSyncedAt(c.getLastSyncedAt())
                .syncConfig(c.getSyncConfig())
                .dataCount(count)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
