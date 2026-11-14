package com.pios.service.llm;

/**
 * LLM 호출 용도. 용도별 max_tokens/temperature 기본값을 가지며 {@code openai.purposes.<key>}로 덮어쓸 수 있다.
 */
public enum LlmPurpose {
    ASK("ask", 500, 0.3),
    WORKOUT_SUMMARY("workout-summary", 1500, 0.3),
    ANOMALY_ALERT("anomaly-alert", 400, 0.4),
    WEEKLY_BRIEFING("weekly-briefing", 700, 0.3);

    private final String key;
    private final int defaultMaxTokens;
    private final double defaultTemperature;

    LlmPurpose(String key, int defaultMaxTokens, double defaultTemperature) {
        this.key = key;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultTemperature = defaultTemperature;
    }

    public String key() {
        return key;
    }

    public int defaultMaxTokens() {
        return defaultMaxTokens;
    }

    public double defaultTemperature() {
        return defaultTemperature;
    }
}
