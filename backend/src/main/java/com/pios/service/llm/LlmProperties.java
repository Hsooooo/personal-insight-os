package com.pios.service.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code openai.*} 설정. 전역 API 키는 사용자별 LLM Provider가 없을 때의 fallback이다.
 */
@Data
@Component
@ConfigurationProperties(prefix = "openai")
public class LlmProperties {

    /** 전역 fallback API 키 (OPENAI_API_KEY). 비어 있으면 사용자 키가 없을 때 LLM을 호출하지 않는다. */
    private String apiKey = "";

    /** OpenAI 호환 API base URL (끝의 / 제외) */
    private String baseUrl = "https://api.openai.com/v1";

    /** 기본 chat 모델 (사용자 Provider에 defaultChatModel이 없을 때) */
    private String model = "gpt-4o-mini";

    /** 기본 embedding 모델 (사용자 Provider에 embeddingModel이 없을 때) */
    private String embeddingModel = "text-embedding-3-small";

    /** 용도별 생성 파라미터 (key: ask, workout-summary, anomaly-alert, weekly-briefing) */
    private Map<String, Purpose> purposes = new HashMap<>();

    @Data
    public static class Purpose {
        private Integer maxTokens;
        private Double temperature;
    }

    public int maxTokensFor(LlmPurpose purpose) {
        Purpose p = purposes.get(purpose.key());
        return p != null && p.getMaxTokens() != null ? p.getMaxTokens() : purpose.defaultMaxTokens();
    }

    public double temperatureFor(LlmPurpose purpose) {
        Purpose p = purposes.get(purpose.key());
        return p != null && p.getTemperature() != null ? p.getTemperature() : purpose.defaultTemperature();
    }

    public String normalizedBaseUrl() {
        String url = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
