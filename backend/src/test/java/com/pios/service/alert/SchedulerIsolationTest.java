package com.pios.service.alert;

import com.pios.domain.User;
import com.pios.dto.InsightDto;
import com.pios.repository.SyncLogRepository;
import com.pios.repository.UserRepository;
import com.pios.service.GarminSyncFinishedEvent;
import com.pios.service.briefing.WeeklyBriefingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 스케줄 작업에서 한 사용자의 실패가 다른 사용자에게 영향을 주지 않는지 검증.
 */
class SchedulerIsolationTest {

    @Test
    void anomalyDetectionContinuesAfterUserFailure() {
        AnomalyAlertService service = mock(AnomalyAlertService.class, Mockito.CALLS_REAL_METHODS);
        doThrow(new RuntimeException("boom")).when(service).detectAndAlert(1L);
        doReturn(List.of(new InsightDto(), new InsightDto())).when(service).detectAndAlert(2L);
        doReturn(List.of(new InsightDto())).when(service).detectAndAlert(3L);

        int alerts = service.detectForUsers(List.of(1L, 2L, 3L));

        assertThat(alerts).isEqualTo(3);
        verify(service).detectAndAlert(3L);
    }

    @Test
    void weeklyBriefingContinuesAfterUserFailure() throws Exception {
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findAll()).thenReturn(List.of(User.builder().id(1L).build(), User.builder().id(2L).build()));
        WeeklyBriefingService service = mock(WeeklyBriefingService.class, Mockito.CALLS_REAL_METHODS);
        var field = WeeklyBriefingService.class.getDeclaredField("userRepo");
        field.setAccessible(true);
        field.set(service, userRepo);
        doThrow(new RuntimeException("llm down")).when(service).generate(1L, false);
        doReturn(new InsightDto()).when(service).generate(2L, false);

        assertThat(service.generateForAllUsers()).isEqualTo(1);
        verify(service).generate(2L, false);
    }

    @Test
    void scheduledSyncCompletionTriggersDetectionAndFallbackSkipsThatUser() {
        AnomalyAlertService alerts = mock(AnomalyAlertService.class);
        UserRepository userRepo = mock(UserRepository.class);
        SyncLogRepository syncLogRepo = mock(SyncLogRepository.class);
        when(userRepo.findAll()).thenReturn(List.of(
                User.builder().id(1L).build(), User.builder().id(2L).build(), User.builder().id(3L).build()));
        when(syncLogRepo.findUserIdsWithRunningSync()).thenReturn(List.of(3L));
        AnomalyAlertScheduleService schedule = new AnomalyAlertScheduleService(alerts, userRepo, syncLogRepo);

        schedule.onSyncFinished(new GarminSyncFinishedEvent(1L, 10L, "COMPLETED", true));
        schedule.onSyncFinished(new GarminSyncFinishedEvent(2L, 11L, "COMPLETED", false)); // 수동 동기화는 무시
        schedule.onSyncFinished(new GarminSyncFinishedEvent(2L, 12L, "FAILED", true));     // 실패는 무시
        verify(alerts).detectAndAlert(1L);
        verify(alerts, never()).detectAndAlert(2L);

        schedule.scheduledDetection();
        // 1: 이미 오늘 감지, 3: 동기화 진행 중 → fallback 대상은 2뿐
        verify(alerts).detectForUsers(List.of(2L));
    }

    @Test
    void postSyncDetectionFailureIsSwallowed() {
        AnomalyAlertService alerts = mock(AnomalyAlertService.class);
        when(alerts.detectAndAlert(anyLong())).thenThrow(new RuntimeException("db down"));
        AnomalyAlertScheduleService schedule = new AnomalyAlertScheduleService(
                alerts, mock(UserRepository.class), mock(SyncLogRepository.class));

        schedule.onSyncFinished(new GarminSyncFinishedEvent(1L, 10L, "PARTIAL", true));
        verify(alerts).detectAndAlert(1L);
    }
}
