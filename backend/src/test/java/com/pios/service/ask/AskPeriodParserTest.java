package com.pios.service.ask;

import com.pios.dto.AskPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AskPeriodParserTest {

    private static final LocalDate REF = LocalDate.of(2026, 6, 20);

    @Test
    void defaultPeriodIsSevenDaysWithTwentyEightDayBaseline() {
        ParsedAskPeriod result = AskPeriodParser.parse("요즘 컨디션이 어때", REF);
        AskPeriod p = result.getPeriod();
        assertThat(result.isExplicit()).isFalse();
        assertThat(p.getStart()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(p.getEnd()).isEqualTo(REF);
        assertThat(p.getBaselineStart()).isEqualTo(LocalDate.of(2026, 5, 17));
        assertThat(p.getBaselineEnd()).isEqualTo(LocalDate.of(2026, 6, 13));
    }

    @Test
    void thisWeek() {
        ParsedAskPeriod result = AskPeriodParser.parse("이번 주 울등 어땠어", REF);
        AskPeriod p = result.getPeriod();
        assertThat(result.isExplicit()).isTrue();
        assertThat(p.getStart()).isEqualTo(LocalDate.of(2026, 6, 15)); // Monday
        assertThat(p.getEnd()).isEqualTo(REF);
        assertThat(p.getBaselineEnd()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(p.getBaselineStart()).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    @Test
    void lastWeek() {
        ParsedAskPeriod result = AskPeriodParser.parse("지난 주 울등 어땠어", REF);
        AskPeriod p = result.getPeriod();
        assertThat(result.isExplicit()).isTrue();
        assertThat(p.getStart()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(p.getEnd()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(p.getBaselineEnd()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(p.getBaselineStart()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void recentNDays() {
        ParsedAskPeriod result = AskPeriodParser.parse("최근 14일 컨디션", REF);
        AskPeriod p = result.getPeriod();
        assertThat(result.isExplicit()).isTrue();
        assertThat(p.getStart()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(p.getEnd()).isEqualTo(REF);
        assertThat(p.getBaselineEnd()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(p.getBaselineStart()).isEqualTo(LocalDate.of(2026, 5, 24));
    }

    @Test
    void recentNWeeks() {
        ParsedAskPeriod result = AskPeriodParser.parse("최근 3주", REF);
        AskPeriod p = result.getPeriod();
        assertThat(result.isExplicit()).isTrue();
        assertThat(p.getStart()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(p.getEnd()).isEqualTo(REF);
        assertThat(p.getBaselineEnd()).isEqualTo(LocalDate.of(2026, 5, 30));
        assertThat(p.getBaselineStart()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void recentOneMonth() {
        ParsedAskPeriod result = AskPeriodParser.parse("최근 한 달", REF);
        AskPeriod p = result.getPeriod();
        assertThat(result.isExplicit()).isTrue();
        assertThat(p.getStart()).isEqualTo(LocalDate.of(2026, 5, 22));
        assertThat(p.getEnd()).isEqualTo(REF);
    }

    @Test
    void clampedToMax90Days() {
        ParsedAskPeriod result = AskPeriodParser.parse("최근 120일", REF);
        AskPeriod p = result.getPeriod();
        long analysisDays = java.time.temporal.ChronoUnit.DAYS.between(p.getStart(), p.getEnd()) + 1;
        assertThat(analysisDays).isEqualTo(90);
    }
}
