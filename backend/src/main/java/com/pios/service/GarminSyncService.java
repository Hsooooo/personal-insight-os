package com.pios.service;

import com.pios.domain.ProviderConnection;
import com.pios.domain.SyncLog;
import com.pios.domain.User;
import com.pios.domain.enums.SyncStatus;
import com.pios.domain.enums.SyncType;
import com.pios.repository.ProviderConnectionRepository;
import com.pios.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneId.of;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarminSyncService {

    private final ProviderConnectionRepository providerRepo;
    private final SyncLogRepository syncLogRepo;
    private final GarminSyncExecutor executor;

    @Value("${sync.rate-limit-seconds:30}")
    private int rateLimitSeconds;

    @Value("${sync.default-full-sync-months:12}")
    private int defaultFullSyncMonths;

    private static final ZoneId KST = of("Asia/Seoul");

    @Transactional
    public SyncLog sync(Long userId, SyncType syncType, LocalDate fromDate, LocalDate toDate) {
        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, "GARMIN")
                .orElseThrow(() -> new IllegalArgumentException("Garmin not connected"));

        checkRateLimit(userId);

        if (fromDate == null || toDate == null) {
            LocalDate today = LocalDate.now(KST);
            if (syncType == SyncType.FULL) {
                fromDate = today.minusMonths(defaultFullSyncMonths);
                toDate = today;
            } else {
                int rangeDays = getSyncRangeDays(conn);
                fromDate = today.minusDays(rangeDays);
                toDate = today;
            }
        }

        SyncLog syncLog = SyncLog.builder()
                .user(User.builder().id(userId).build())
                .providerType("GARMIN")
                .syncType(syncType.name())
                .status(SyncStatus.RUNNING.name())
                .dateFrom(fromDate)
                .dateTo(toDate)
                .startedAt(Instant.now())
                .build();
        syncLog = syncLogRepo.saveAndFlush(syncLog);

        if (syncLog.getId() == null) {
            throw new IllegalStateException("Failed to create sync log");
        }

        executor.runSyncAsync(userId, syncLog.getId(), fromDate, toDate);

        return syncLog;
    }

    private void checkRateLimit(Long userId) {
        Optional<SyncLog> latest = syncLogRepo.findLatestByUserIdAndProviderType(userId, "GARMIN");
        if (latest.isPresent()) {
            SyncLog log = latest.get();
            if (SyncStatus.RUNNING.name().equals(log.getStatus())) {
                throw new TooFrequentSyncException("Sync is already running");
            }
            Instant lastStarted = log.getStartedAt();
            if (lastStarted != null &&
                Duration.between(lastStarted, Instant.now()).getSeconds() < rateLimitSeconds) {
                throw new TooFrequentSyncException(
                        "Please wait " + rateLimitSeconds + " seconds between syncs");
            }
        }
    }

    private int getSyncRangeDays(ProviderConnection conn) {
        Map<String, Object> config = conn.getSyncConfig();
        if (config != null && config.get("sync_range_days") instanceof Number n) {
            return n.intValue();
        }
        return 7;
    }

    public static class TooFrequentSyncException extends RuntimeException {
        public TooFrequentSyncException(String message) {
            super(message);
        }
    }
}
