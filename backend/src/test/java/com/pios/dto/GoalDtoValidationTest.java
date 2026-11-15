package com.pios.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoalDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsOfficialRaceDistances() {
        // 하프 21.0975km는 소수 4자리 — 예전에는 @Digits(fraction=3)에 걸려 400이 났다
        for (String km : new String[]{"5", "10", "21.0975", "42.195"}) {
            var dto = GoalDto.builder().raceDistanceKm(new BigDecimal(km)).build();
            assertThat(validator.validate(dto)).as(km).isEmpty();
        }
    }

    @Test
    void rejectsNonPositiveOrHugeDistance() {
        assertThat(validator.validate(GoalDto.builder().raceDistanceKm(BigDecimal.ZERO).build())).isNotEmpty();
        assertThat(validator.validate(GoalDto.builder().raceDistanceKm(new BigDecimal("500")).build())).isNotEmpty();
    }
}
