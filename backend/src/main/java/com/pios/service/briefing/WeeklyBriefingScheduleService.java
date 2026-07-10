package com.pios.service.briefing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBriefingScheduleService {

    private final WeeklyBriefingService weeklyBriefingService;

    /**
     * 매주 일요일 13:00 KST 주간 브리핑 생성.
     * Garmin sync(12:00 KST) 이후 실행되도록 기본 cron을 맞춤.
     */
    @Scheduled(cron = "${briefing.schedule.cron:0 0 13 * * SUN}", zone = "${app.timezone:Asia/Seoul}")
    public void scheduledBriefing() {
        log.info("Starting scheduled weekly briefing");
        int count = weeklyBriefingService.generateForAllUsers();
        log.info("Scheduled weekly briefing completed. Processed {} users", count);
    }
}
