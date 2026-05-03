package com.pios.service;

import com.pios.domain.ProviderConnection;
import com.pios.domain.enums.SyncType;
import com.pios.repository.ProviderConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncScheduleService {

    private final ProviderConnectionRepository providerRepo;
    private final GarminSyncService garminSyncService;

    /**
     * 매일 새벽 3시에 자동 동기화 실행
     */
    @Scheduled(cron = "${sync.schedule.cron:0 0 3 * * *}")
    public void scheduledSync() {
        log.info("Starting scheduled Garmin sync");

        List<ProviderConnection> connections = providerRepo.findAll().stream()
                .filter(c -> "GARMIN".equals(c.getProviderType()))
                .filter(c -> "CONNECTED".equals(c.getConnectionStatus()))
                .filter(this::isAutoSyncEnabled)
                .toList();

        for (ProviderConnection conn : connections) {
            Long userId = conn.getUser().getId();
            try {
                log.info("Auto-syncing user {}", userId);
                garminSyncService.sync(userId, SyncType.INCREMENTAL, null, null);
            } catch (Exception e) {
                log.error("Auto-sync failed for user {}", userId, e);
            }
        }

        log.info("Scheduled sync completed. Processed {} users", connections.size());
    }

    private boolean isAutoSyncEnabled(ProviderConnection conn) {
        Map<String, Object> config = conn.getSyncConfig();
        if (config == null) return true;
        Object enabled = config.get("auto_sync_enabled");
        if (enabled instanceof Boolean b) return b;
        if (enabled instanceof String s) return Boolean.parseBoolean(s);
        return true;
    }
}
