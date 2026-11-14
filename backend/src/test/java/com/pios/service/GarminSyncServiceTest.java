package com.pios.service;

import com.pios.domain.ProviderConnection;
import com.pios.domain.SyncLog;
import com.pios.domain.User;
import com.pios.domain.enums.SyncType;
import com.pios.repository.ProviderConnectionRepository;
import com.pios.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GarminSyncServiceTest {

    @Mock ProviderConnectionRepository providerRepo;
    @Mock SyncLogRepository syncLogRepo;
    @Mock GarminSyncExecutor executor;
    @Mock PlatformTransactionManager txManager;

    private GarminSyncService service;
    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to = LocalDate.of(2026, 1, 7);

    @BeforeEach
    void setUp() {
        service = new GarminSyncService(providerRepo, syncLogRepo, executor, txManager);
        when(providerRepo.findByUserIdAndProviderType(1L, "GARMIN")).thenReturn(Optional.of(
                ProviderConnection.builder().user(User.builder().id(1L).build()).providerType("GARMIN").build()));
        when(executor.maxExpectedDuration(any(), any())).thenReturn(Duration.ofMinutes(30));
        when(syncLogRepo.saveAndFlush(any())).thenAnswer(inv -> {
            SyncLog log = inv.getArgument(0);
            log.setId(99L);
            return log;
        });
    }

    private SyncLog running(Instant startedAt) {
        return SyncLog.builder().id(5L).status("RUNNING").providerType("GARMIN")
                .dateFrom(from).dateTo(to).startedAt(startedAt).build();
    }

    @Test
    void freshRunningSyncBlocksNewSync() {
        when(syncLogRepo.findLatestByUserIdAndProviderType(1L, "GARMIN"))
                .thenReturn(Optional.of(running(Instant.now().minusSeconds(60))));

        assertThatThrownBy(() -> service.sync(1L, SyncType.MANUAL, from, to))
                .isInstanceOf(GarminSyncService.TooFrequentSyncException.class)
                .hasMessageContaining("already running");
        verify(executor, never()).runSyncAsync(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void staleRunningSyncIsMarkedFailedAndNewSyncStarts() {
        SyncLog stale = running(Instant.now().minus(Duration.ofHours(3)));
        when(syncLogRepo.findLatestByUserIdAndProviderType(1L, "GARMIN")).thenReturn(Optional.of(stale));

        SyncLog created = service.sync(1L, SyncType.MANUAL, from, to);

        assertThat(stale.getStatus()).isEqualTo("FAILED");
        assertThat(stale.getErrorMessage()).contains("stale");
        verify(syncLogRepo).save(stale);
        assertThat(created.getId()).isEqualTo(99L);
        verify(executor).runSyncAsync(1L, 99L, from, to, false);
    }

    @Test
    void concurrentInsertViolatingRunningIndexIsReportedAsAlreadyRunning() {
        when(syncLogRepo.findLatestByUserIdAndProviderType(1L, "GARMIN")).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("uq_sync_logs_running")).when(syncLogRepo).saveAndFlush(any());

        assertThatThrownBy(() -> service.sync(1L, SyncType.MANUAL, from, to))
                .isInstanceOf(GarminSyncService.TooFrequentSyncException.class);
    }

    @Test
    void rejectedQueueMarksLogFailed() {
        when(syncLogRepo.findLatestByUserIdAndProviderType(1L, "GARMIN")).thenReturn(Optional.empty());
        doThrow(new TaskRejectedException("full")).when(executor)
                .runSyncAsync(any(), any(), any(), any(), anyBoolean());

        assertThatThrownBy(() -> service.sync(1L, SyncType.MANUAL, from, to, true))
                .isInstanceOf(GarminSyncService.TooFrequentSyncException.class)
                .hasMessageContaining("queue");
        verify(syncLogRepo).save(argThat(l -> "FAILED".equals(l.getStatus())));
    }

    @Test
    void invalidRangeIsRejected() {
        assertThatThrownBy(() -> service.sync(1L, SyncType.MANUAL, to, from))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // startup sweeper behaviour is covered by SyncLogStartupSweeperTest
}
