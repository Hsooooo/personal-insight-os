package com.pios.service.ask;

import com.pios.dto.AskPeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AskPeriodParser {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final int DEFAULT_ANALYSIS_DAYS = 7;
    public static final int DEFAULT_BASELINE_DAYS = 28;
    public static final int MAX_ANALYSIS_DAYS = 90;

    private static final Pattern DAYS_PATTERN = Pattern.compile("최근\\s*(\\d+)\\s*일");
    private static final Pattern WEEKS_PATTERN = Pattern.compile("최근\\s*(\\d+)\\s*주");
    private static final Pattern MONTHS_PATTERN = Pattern.compile("최근\\s*(\\d+)\\s*개월");

    public static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    public static ParsedAskPeriod parse(String question, LocalDate referenceDate) {
        String q = question.toLowerCase().replaceAll("\\s+", "");

        // 이번 주
        if (q.contains("이번주")) {
            LocalDate monday = referenceDate.with(WeekFields.ISO.dayOfWeek(), 1);
            AskPeriod period = buildPeriod(monday, referenceDate, 7);
            return ParsedAskPeriod.builder().period(period).explicit(true).build();
        }

        // 지난 주
        if (q.contains("지난주") || q.contains("저번주")) {
            LocalDate thisMonday = referenceDate.with(WeekFields.ISO.dayOfWeek(), 1);
            LocalDate lastMonday = thisMonday.minusDays(7);
            LocalDate lastSunday = thisMonday.minusDays(1);
            AskPeriod period = buildEqualBaseline(lastMonday, lastSunday);
            return ParsedAskPeriod.builder().period(period).explicit(true).build();
        }

        // 최근 N개월
        Matcher monthsMatcher = MONTHS_PATTERN.matcher(q);
        if (monthsMatcher.find()) {
            int months = Integer.parseInt(monthsMatcher.group(1));
            int days = clampAnalysisDays(months * 30);
            return buildRecent(referenceDate, days, true);
        }

        // 최근 한 달 / 최근 1개월 / 최근 30일
        if (q.contains("최근한달") || q.contains("최근1개월") || q.contains("최근30일")) {
            return buildRecent(referenceDate, clampAnalysisDays(30), true);
        }

        // 최근 N주
        Matcher weeksMatcher = WEEKS_PATTERN.matcher(q);
        if (weeksMatcher.find()) {
            int weeks = Integer.parseInt(weeksMatcher.group(1));
            return buildRecent(referenceDate, clampAnalysisDays(weeks * 7), true);
        }

        // 최근 N일
        Matcher daysMatcher = DAYS_PATTERN.matcher(q);
        if (daysMatcher.find()) {
            int days = Integer.parseInt(daysMatcher.group(1));
            return buildRecent(referenceDate, clampAnalysisDays(days), true);
        }

        // 기본: 최근 7일 + 직전 28일 기준선
        AskPeriod defaultPeriod = AskPeriod.builder()
                .start(referenceDate.minusDays(DEFAULT_ANALYSIS_DAYS - 1))
                .end(referenceDate)
                .baselineStart(referenceDate.minusDays(DEFAULT_ANALYSIS_DAYS + DEFAULT_BASELINE_DAYS - 1))
                .baselineEnd(referenceDate.minusDays(DEFAULT_ANALYSIS_DAYS))
                .build();
        return ParsedAskPeriod.builder().period(defaultPeriod).explicit(false).build();
    }

    private static ParsedAskPeriod buildRecent(LocalDate referenceDate, int analysisDays, boolean explicit) {
        LocalDate start = referenceDate.minusDays(analysisDays - 1);
        LocalDate end = referenceDate;
        LocalDate baselineEnd = start.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(analysisDays - 1);
        AskPeriod period = AskPeriod.builder()
                .start(start)
                .end(end)
                .baselineStart(baselineStart)
                .baselineEnd(baselineEnd)
                .build();
        return ParsedAskPeriod.builder().period(period).explicit(explicit).build();
    }

    private static AskPeriod buildPeriod(LocalDate analysisStart, LocalDate analysisEnd, int baselineDays) {
        LocalDate baselineEnd = analysisStart.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(baselineDays - 1);
        return AskPeriod.builder()
                .start(analysisStart)
                .end(analysisEnd)
                .baselineStart(baselineStart)
                .baselineEnd(baselineEnd)
                .build();
    }

    private static AskPeriod buildEqualBaseline(LocalDate analysisStart, LocalDate analysisEnd) {
        long days = ChronoUnit.DAYS.between(analysisStart, analysisEnd) + 1;
        return buildPeriod(analysisStart, analysisEnd, (int) days);
    }

    private static int clampAnalysisDays(int days) {
        return Math.min(days, MAX_ANALYSIS_DAYS);
    }
}
