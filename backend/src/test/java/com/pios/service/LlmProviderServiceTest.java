package com.pios.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderServiceTest {

    @Test
    void onlyOpenAiProvidersAreUsedForOpenAiEndpoints() {
        assertThat(LlmProviderService.isOpenAiCompatible("OpenAI")).isTrue();
        assertThat(LlmProviderService.isOpenAiCompatible(" openai ")).isTrue();
        assertThat(LlmProviderService.isOpenAiCompatible(null)).isTrue();
        assertThat(LlmProviderService.isOpenAiCompatible("")).isTrue();

        // 다른 회사의 키가 OpenAI로 전송되면 안 된다
        assertThat(LlmProviderService.isOpenAiCompatible("Anthropic")).isFalse();
        assertThat(LlmProviderService.isOpenAiCompatible("Google")).isFalse();
    }
}
