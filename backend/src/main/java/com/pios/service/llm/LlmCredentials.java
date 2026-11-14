package com.pios.service.llm;

/**
 * 사용자에게 적용되는 LLM 자격증명/모델.
 *
 * @param source USER(사용자 LLM Provider) 또는 GLOBAL(OPENAI_API_KEY)
 */
public record LlmCredentials(String apiKey, String providerName, String chatModel, String embeddingModel, Source source) {

    public enum Source { USER, GLOBAL }

    @Override
    public String toString() {
        // API 키가 로그에 남지 않도록 마스킹
        return "LlmCredentials[provider=" + providerName + ", chatModel=" + chatModel
                + ", embeddingModel=" + embeddingModel + ", source=" + source + "]";
    }
}
