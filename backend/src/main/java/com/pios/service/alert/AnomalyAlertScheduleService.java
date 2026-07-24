package com.pios.service.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyAlertScheduleService {

    private final AnomalyAlertService anomalyAlertService;

    /**
     * Garmin sync(12:00 KST) 이후 실행되도록 기본 cron을 맞춤.
     * zone을 Asia/Seoul로 명시해 컨테이너 UTC와 무관하게 KST 기준으로 동작한다.
     */
    @Scheduled(cron = "${alert.schedule.cron:0 30 12 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public void scheduledDetection() {
        log.info("Starting scheduled anomaly detection");
        int alerts = anomalyAlertService.detectForAllUsers();
        log.info("Scheduled anomaly detection done: {} alert(s) created", alerts);
    }
}
