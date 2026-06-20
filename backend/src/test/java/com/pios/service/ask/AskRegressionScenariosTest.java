package com.pios.service.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AskRegressionScenariosTest {

    record Scenario(String question, String intent, boolean explicitPeriod) {
    }

    static Stream<Arguments> scenarios() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = AskRegressionScenariosTest.class.getResourceAsStream("/ask-regression-scenarios.json")) {
            List<Scenario> list = mapper.readValue(is,
                    mapper.getTypeFactory().constructCollectionType(List.class, Scenario.class));
            return list.stream().map(s -> Arguments.of(s.question(), s.intent(), s.explicitPeriod()));
        }
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("scenarios")
    void classifyAndParse(String question, String expectedIntent, boolean expectedExplicit) {
        AskIntent intent = AskIntentClassifier.classify(question);
        ParsedAskPeriod period = AskPeriodParser.parse(question, LocalDate.of(2026, 6, 20));

        assertThat(intent.name())
                .as("question=[%s]", question)
                .isEqualTo(expectedIntent);
        assertThat(period.isExplicit()).isEqualTo(expectedExplicit);
        assertThat(period.getPeriod().getStart()).isNotNull();
        assertThat(period.getPeriod().getEnd()).isNotNull();
        assertThat(period.getPeriod().getBaselineStart()).isNotNull();
        assertThat(period.getPeriod().getBaselineEnd()).isNotNull();
    }

    @Test
    void maxAnalysisDaysIsLimitedTo90() {
        ParsedAskPeriod period = AskPeriodParser.parse("최근 120일 수면", LocalDate.of(2026, 6, 20));
        long days = java.time.temporal.ChronoUnit.DAYS.between(
                period.getPeriod().getStart(), period.getPeriod().getEnd()) + 1;
        assertThat(days).isEqualTo(90);
    }
}
