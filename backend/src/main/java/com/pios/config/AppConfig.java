package com.pios.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    @Value("${neo4j.uri}")
    private String neo4jUri;

    @Value("${neo4j.username}")
    private String neo4jUsername;

    @Value("${neo4j.password}")
    private String neo4jPassword;

    @Value("${neo4j.connection-timeout:10s}")
    private Duration neo4jConnectionTimeout;

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver() {
        Config config = Config.builder()
                .withConnectionTimeout(neo4jConnectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .withConnectionAcquisitionTimeout(neo4jConnectionTimeout.toMillis() * 3, TimeUnit.MILLISECONDS)
                // 그래프 쓰기는 부가 작업이므로 Neo4j 장애 시 오래 재시도하지 않는다 (기본 30s)
                .withMaxTransactionRetryTime(5, TimeUnit.SECONDS)
                .build();
        return GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword), config);
    }

    /**
     * 범용 외부 HTTP 클라이언트 (날씨 등). 타임아웃 없이 무한 대기하지 않도록 connect/read 타임아웃을 건다.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${http.client.connect-timeout:5s}") Duration connectTimeout,
                                     @Value("${http.client.read-timeout:20s}") Duration readTimeout) {
        return builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }

    /**
     * LLM(OpenAI 호환) 전용 클라이언트. 생성 응답이 느릴 수 있어 read 타임아웃을 별도로 둔다.
     */
    @Bean
    public RestTemplate llmRestTemplate(RestTemplateBuilder builder,
                                        @Value("${openai.connect-timeout:5s}") Duration connectTimeout,
                                        @Value("${openai.read-timeout:60s}") Duration readTimeout) {
        return builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }
}
