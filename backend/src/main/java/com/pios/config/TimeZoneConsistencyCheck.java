package com.pios.config;

import com.pios.common.AppTimeZones;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * 스케줄 cron(zone=${app.timezone})과 일 단위 집계({@link AppTimeZones#KST})가 같은 타임존을 쓰는지 확인한다.
 */
@Slf4j
@Component
public class TimeZoneConsistencyCheck {

    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

    @PostConstruct
    void check() {
        if (!ZoneId.of(appTimezone).getRules().equals(AppTimeZones.KST.getRules())) {
            log.warn("app.timezone={} differs from AppTimeZones.KST={}; schedules and daily aggregation may disagree",
                    appTimezone, AppTimeZones.KST);
        }
    }
}
