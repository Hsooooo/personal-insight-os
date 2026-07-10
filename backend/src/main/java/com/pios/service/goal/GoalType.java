package com.pios.service.goal;

import java.util.Arrays;
import java.util.Optional;

/**
 * 자동 진행률 추적이 가능한 목표 유형 템플릿.
 * 매핑되지 않은 goalType 문자열은 progress=null 로 둔다.
 */
public enum GoalType {
    WEEKLY_RUN_DISTANCE("km", "주간 러닝 거리"),
    WEEKLY_ACTIVITY_COUNT("회", "주간 운동 횟수"),
    SLEEP_HOURS("h", "평균 수면 시간"),
    WEIGHT_KG("kg", "체중");

    private final String defaultUnit;
    private final String label;

    GoalType(String defaultUnit, String label) {
        this.defaultUnit = defaultUnit;
        this.label = label;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<GoalType> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return Arrays.stream(values())
                .filter(t -> t.name().equals(normalized))
                .findFirst();
    }
}
