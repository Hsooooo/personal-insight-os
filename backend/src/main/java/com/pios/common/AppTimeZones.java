package com.pios.common;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 앱 전역 타임존. 스케줄·일 단위 집계·동기화 기간은 모두 KST 기준.
 */
public final class AppTimeZones {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private AppTimeZones() {}

    public static LocalDate todayKst() {
        return LocalDate.now(KST);
    }
}
