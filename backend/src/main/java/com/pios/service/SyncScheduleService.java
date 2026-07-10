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
     * 매일 정오(KST 12:00)에 자동 동기화.
     * 전날 수면이 Garmin에 충분히 반영된 뒤 수집하기 위함.
     * zone을 Asia/Seoul로 명시해 컨테이너 UTC와 무관하게 KST 기준으로 동작한다.
     */
    @Scheduled(cron = "${sync.schedule.cron:0 0 12 * * *}", zone = "${app.timezone:Asia/Seoul}")
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
