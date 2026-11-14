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
     * 매일 정오(KST 12:00)에 자동 동기화 (전날 수면이 Garmin에 반영된 뒤 수집).
     * 동기화는 sync 전용 풀에 제출만 하고 즉시 반환한다. 큐가 가득 차면 해당 사용자는 건너뛴다.
     * 각 사용자의 동기화가 끝나면 {@link GarminSyncFinishedEvent}(scheduled=true)로 이상 신호 감지가 이어진다.
     */
    @Scheduled(cron = "${sync.schedule.cron:0 0 12 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public void scheduledSync() {
        log.info("Starting scheduled Garmin sync");

        List<ProviderConnection> connections = providerRepo
                .findByProviderTypeAndConnectionStatus("GARMIN", "CONNECTED").stream()
                .filter(SyncScheduleService::isAutoSyncEnabled)
                .toList();

        int submitted = 0;
        for (ProviderConnection conn : connections) {
            Long userId = conn.getUser().getId();
            try {
                garminSyncService.sync(userId, SyncType.INCREMENTAL, null, null, true);
                submitted++;
            } catch (GarminSyncService.TooFrequentSyncException e) {
                log.info("Skipping scheduled sync for user {}: {}", userId, e.getMessage());
            } catch (Exception e) {
                log.error("Auto-sync failed to start for user {}", userId, e);
            }
        }

        log.info("Scheduled sync submitted for {}/{} users", submitted, connections.size());
    }

    static boolean isAutoSyncEnabled(ProviderConnection conn) {
        Map<String, Object> config = conn.getSyncConfig();
        if (config == null) return true;
        Object enabled = config.get("auto_sync_enabled");
        if (enabled instanceof Boolean b) return b;
        if (enabled instanceof String s) return Boolean.parseBoolean(s);
        return true;
    }
}
