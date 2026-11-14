package com.pios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pios.domain.ProviderConnection;
import com.pios.domain.SyncLog;
import com.pios.domain.User;
import com.pios.domain.enums.SyncStatus;
import com.pios.repository.ProviderConnectionRepository;
import com.pios.repository.SyncLogRepository;
import com.pios.security.SecretCryptoService;
import com.pios.service.garmin.ActivityWeatherEnricher;
import com.pios.service.garmin.GarminActivityPersister;
import com.pios.service.garmin.GarminDailyPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GarminSyncExecutorTest {

    @Mock ProviderConnectionRepository providerRepo;
    @Mock SyncLogRepository syncLogRepo;
    @Mock GarminPythonClient pythonClient;
    @Mock GarminActivityPersister activityPersister;
    @Mock GarminDailyPersister dailyPersister;
    @Mock ActivityWeatherEnricher weatherEnricher;
    @Mock GraphProjectorService graphProjector;
    @Mock SecretCryptoService secretCrypto;
    @Mock PlatformTransactionManager txManager;
    @Mock ApplicationEventPublisher eventPublisher;

    private GarminSyncExecutor executor;
    private SyncLog syncLog;
    private final ObjectMapper om = new ObjectMapper();
    private final LocalDate from = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        executor = new GarminSyncExecutor(providerRepo, syncLogRepo, pythonClient, activityPersister, dailyPersister,
                weatherEnricher, graphProjector, secretCrypto, new TransactionTemplate(txManager), eventPublisher);
        syncLog = SyncLog.builder().id(10L).status("RUNNING").startedAt(Instant.now()).build();
        Map<String, Object> auth = new HashMap<>();
        auth.put("email", "a@b.c");
        auth.put("password", "ENC:x");
        ProviderConnection conn = ProviderConnection.builder()
                .user(User.builder().id(1L).build()).providerType("GARMIN").authPayload(auth).build();
        when(syncLogRepo.findById(10L)).thenReturn(Optional.of(syncLog));
        when(providerRepo.findByUserIdAndProviderType(1L, "GARMIN")).thenReturn(Optional.of(conn));
        when(secretCrypto.decrypt(any())).thenReturn("pw");
        when(activityPersister.save(anyLong(), any())).thenReturn(new GarminActivityPersister.Result(1, List.of()));
        when(dailyPersister.saveHealthMetrics(anyLong(), any())).thenReturn(1);
    }

    private GarminPythonClient.SyncResult result(List<GarminPythonClient.EndpointError> errors,
                                                 GarminPythonClient.FatalError fatal) {
        return new GarminPythonClient.SyncResult(om.createObjectNode(), errors, fatal);
    }

    private SyncStatus run(LocalDate to) {
        executor.runSyncAsync(1L, 10L, from, to, true);
        ArgumentCaptor<GarminSyncFinishedEvent> event = ArgumentCaptor.forClass(GarminSyncFinishedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().scheduled()).isTrue();
        assertThat(event.getValue().status()).isEqualTo(syncLog.getStatus());
        return SyncStatus.valueOf(syncLog.getStatus());
    }

    @Test
    void allChunksSucceed_completed() {
        when(pythonClient.fetch(any(), any(), any(), any(), any(), any())).thenReturn(result(List.of(), null));

        assertThat(run(from.plusDays(10))).isEqualTo(SyncStatus.COMPLETED);
        verify(pythonClient, times(2)).fetch(any(), any(), any(), any(), any(), any()); // 7일 청크 2개
        assertThat(syncLog.getErrorMessage()).isNull();
        verify(graphProjector).projectUserData(1L);
    }

    @Test
    void endpointErrors_partialWithSummary() {
        when(pythonClient.fetch(any(), any(), any(), any(), any(), any())).thenReturn(result(List.of(
                new GarminPythonClient.EndpointError("stats", "2026-01-01", "server", "HTTP 500"),
                new GarminPythonClient.EndpointError("stats", "2026-01-02", "server", "HTTP 500")), null));

        assertThat(run(from.plusDays(2))).isEqualTo(SyncStatus.PARTIAL);
        assertThat(syncLog.getErrorMessage()).contains("stats (server) x2");
        assertThat(syncLog.getActivitiesCount()).isEqualTo(1);
    }

    @Test
    void rateLimitInOutput_failedAndStopsRemainingChunks() {
        when(pythonClient.fetch(any(), any(), any(), any(), any(), any())).thenReturn(result(List.of(),
                new GarminPythonClient.FatalError("rate_limit", "429 Too Many Requests", "stats")));

        assertThat(run(from.plusDays(20))).isEqualTo(SyncStatus.FAILED);
        verify(pythonClient, times(1)).fetch(any(), any(), any(), any(), any(), any());
        assertThat(syncLog.getErrorMessage()).contains("rate_limit");
        // 수집된 데이터는 저장됨
        assertThat(syncLog.getActivitiesCount()).isEqualTo(1);
    }

    @Test
    void authFailure_failed() {
        when(pythonClient.fetch(any(), any(), any(), any(), any(), any()))
                .thenThrow(new GarminFetchException(GarminFetchException.Kind.AUTH, "login failed"));

        assertThat(run(from.plusDays(20))).isEqualTo(SyncStatus.FAILED);
        verify(pythonClient, times(1)).fetch(any(), any(), any(), any(), any(), any());
        verify(providerRepo, never()).save(any());
    }

    @Test
    void oneChunkTimesOut_partial() {
        when(pythonClient.fetch(any(), any(), any(), eq(from), any(), any()))
                .thenThrow(new GarminFetchException(GarminFetchException.Kind.TIMEOUT, "timed out"));
        when(pythonClient.fetch(any(), any(), any(), eq(from.plusDays(7)), any(), any()))
                .thenReturn(result(List.of(), null));

        assertThat(run(from.plusDays(10))).isEqualTo(SyncStatus.PARTIAL);
        assertThat(syncLog.getErrorMessage()).contains("timed out");
    }

    @Test
    void graphFailureDoesNotChangeStatus() {
        when(pythonClient.fetch(any(), any(), any(), any(), any(), any())).thenReturn(result(List.of(), null));
        doThrow(new RuntimeException("neo4j down")).when(graphProjector).projectUserData(1L);

        assertThat(run(from)).isEqualTo(SyncStatus.COMPLETED);
    }

    @Test
    void chunksAreSplitByChunkDays() {
        assertThat(GarminSyncExecutor.splitIntoChunks(from, from.plusDays(13), 7)).hasSize(2);
        assertThat(GarminSyncExecutor.splitIntoChunks(from, from, 7)).hasSize(1);
    }
}
