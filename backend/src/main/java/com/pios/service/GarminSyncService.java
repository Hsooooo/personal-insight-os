package com.pios.service;

import com.pios.common.AppTimeZones;
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
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Garmin 동기화 시작점. 사용자/Provider당 RUNNING은 하나만 허용한다
 * (애플리케이션 검사 + DB 부분 유니크 인덱스 uq_sync_logs_running으로 경쟁 상태 방지).
 * 오래된 RUNNING(stale)은 FAILED로 정리한 뒤 새 동기화를 허용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GarminSyncService {

    static final String PROVIDER = "GARMIN";
    private static final long MAX_SYNC_RANGE_DAYS = 3L * 366;

    private final ProviderConnectionRepository providerRepo;
    private final SyncLogRepository syncLogRepo;
    private final GarminSyncExecutor executor;
    private final PlatformTransactionManager transactionManager;

    @Value("${sync.rate-limit-seconds:30}")
    private int rateLimitSeconds = 30;

    @Value("${sync.default-full-sync-months:12}")
    private int defaultFullSyncMonths = 12;

    public SyncLog sync(Long userId, SyncType syncType, LocalDate fromDate, LocalDate toDate) {
        return sync(userId, syncType, fromDate, toDate, false);
    }

    public SyncLog sync(Long userId, SyncType syncType, LocalDate fromDate, LocalDate toDate, boolean scheduled) {
        ProviderConnection conn = providerRepo.findByUserIdAndProviderType(userId, PROVIDER)
                .orElseThrow(() -> new IllegalArgumentException("Garmin not connected"));

        if (fromDate == null || toDate == null) {
            LocalDate today = AppTimeZones.todayKst();
            if (syncType == SyncType.FULL) {
                fromDate = today.minusMonths(defaultFullSyncMonths);
            } else {
                fromDate = today.minusDays(getSyncRangeDays(conn));
            }
            toDate = today;
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("dateFrom must be on or before dateTo");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_SYNC_RANGE_DAYS) {
            throw new IllegalArgumentException("Sync range must not exceed " + MAX_SYNC_RANGE_DAYS + " days");
        }

        checkRateLimit(userId);

        SyncLog syncLog = createSyncLogInNewTransaction(userId, syncType, fromDate, toDate);
        try {
            executor.runSyncAsync(userId, syncLog.getId(), fromDate, toDate, scheduled);
        } catch (TaskRejectedException e) {
            log.warn("Sync queue full; rejecting sync for user {}", userId);
            markFailed(syncLog, "Sync queue is full");
            throw new TooFrequentSyncException("Sync queue is full. Please try again later");
        }
        return syncLog;
    }

    private SyncLog createSyncLogInNewTransaction(Long userId, SyncType syncType,
                                                  LocalDate fromDate, LocalDate toDate) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        SyncLog syncLog;
        try {
            syncLog = txTemplate.execute(status -> syncLogRepo.saveAndFlush(SyncLog.builder()
                    .user(User.builder().id(userId).build())
                    .providerType(PROVIDER)
                    .syncType(syncType.name())
                    .status(SyncStatus.RUNNING.name())
                    .dateFrom(fromDate)
                    .dateTo(toDate)
                    .activitiesCount(0)
                    .healthMetricsCount(0)
                    .sleepCount(0)
                    .weightsCount(0)
                    .startedAt(Instant.now())
                    .build()));
        } catch (DataIntegrityViolationException e) {
            // uq_sync_logs_running: 동시에 들어온 다른 요청이 먼저 RUNNING을 만들었다
            throw new TooFrequentSyncException("Sync is already running");
        }
        if (syncLog == null || syncLog.getId() == null) {
            throw new IllegalStateException("Failed to create sync log");
        }
        return syncLog;
    }

    private void checkRateLimit(Long userId) {
        Optional<SyncLog> latest = syncLogRepo.findLatestByUserIdAndProviderType(userId, PROVIDER);
        if (latest.isEmpty()) {
            return;
        }
        SyncLog latestLog = latest.get();
        if (SyncStatus.RUNNING.name().equals(latestLog.getStatus())) {
            if (!isStale(latestLog)) {
                throw new TooFrequentSyncException("Sync is already running");
            }
            log.warn("Marking stale RUNNING sync log {} (user {}, started {}) as FAILED",
                    latestLog.getId(), userId, latestLog.getStartedAt());
            markFailed(latestLog, "Sync did not finish in time (stale RUNNING)");
            return;
        }
        Instant lastStarted = latestLog.getStartedAt();
        if (lastStarted != null && Duration.between(lastStarted, Instant.now()).getSeconds() < rateLimitSeconds) {
            throw new TooFrequentSyncException("Please wait " + rateLimitSeconds + " seconds between syncs");
        }
    }

    boolean isStale(SyncLog syncLog) {
        if (syncLog.getStartedAt() == null) {
            return true;
        }
        Duration limit = executor.maxExpectedDuration(syncLog.getDateFrom(), syncLog.getDateTo());
        return syncLog.getStartedAt().plus(limit).isBefore(Instant.now());
    }

    private void markFailed(SyncLog syncLog, String message) {
        syncLog.setStatus(SyncStatus.FAILED.name());
        syncLog.setErrorMessage(message);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepo.save(syncLog);
    }

    private int getSyncRangeDays(ProviderConnection conn) {
        Map<String, Object> config = conn.getSyncConfig();
        if (config != null && config.get("sync_range_days") instanceof Number n && n.intValue() > 0) {
            return Math.min(n.intValue(), 365);
        }
        return 7;
    }

    public static class TooFrequentSyncException extends RuntimeException {
        public TooFrequentSyncException(String message) {
            super(message);
        }
    }
}
