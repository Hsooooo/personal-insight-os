package com.pios.service.alert;

import com.pios.common.AppTimeZones;
import com.pios.domain.User;
import com.pios.domain.enums.SyncStatus;
import com.pios.repository.SyncLogRepository;
import com.pios.repository.UserRepository;
import com.pios.service.GarminSyncFinishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이상 신호 감지 스케줄링.
 * <ol>
 *   <li>스케줄 동기화가 끝난 사용자는 즉시 감지 (최신 데이터 기준) — {@link #onSyncFinished}</li>
 *   <li>cron(기본 12:30 KST)은 fallback: 오늘 아직 감지하지 않았고 동기화가 진행 중이 아닌 사용자만 처리</li>
 * </ol>
 * 감지 결과 자체는 제목 기준 3일 중복 억제가 있어 중복 실행돼도 알림이 반복되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyAlertScheduleService {

    private final AnomalyAlertService anomalyAlertService;
    private final UserRepository userRepo;
    private final SyncLogRepository syncLogRepo;

    /** 사용자별 마지막 감지 일자 (단일 인스턴스 기준, 재시작 시 초기화되어도 중복 억제로 안전) */
    private final Map<Long, LocalDate> lastDetected = new ConcurrentHashMap<>();

    @EventListener
    public void onSyncFinished(GarminSyncFinishedEvent event) {
        if (!event.scheduled() || SyncStatus.FAILED.name().equals(event.status())) {
            return;
        }
        try {
            int alerts = anomalyAlertService.detectAndAlert(event.userId()).size();
            lastDetected.put(event.userId(), AppTimeZones.todayKst());
            log.info("Post-sync anomaly detection for user {}: {} alert(s)", event.userId(), alerts);
        } catch (Exception e) {
            log.error("Post-sync anomaly detection failed for user {}", event.userId(), e);
        }
    }

    @Scheduled(cron = "${alert.schedule.cron:0 30 12 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public void scheduledDetection() {
        LocalDate today = AppTimeZones.todayKst();
        Set<Long> running = new HashSet<>(syncLogRepo.findUserIdsWithRunningSync());
        List<Long> targets = userRepo.findAll().stream()
                .map(User::getId)
                .filter(id -> !today.equals(lastDetected.get(id)))
                .filter(id -> !running.contains(id))
                .toList();
        log.info("Starting fallback anomaly detection for {} user(s) ({} still syncing)", targets.size(), running.size());
        int alerts = anomalyAlertService.detectForUsers(targets);
        targets.forEach(id -> lastDetected.put(id, today));
        log.info("Fallback anomaly detection done: {} alert(s) created", alerts);
    }
}
