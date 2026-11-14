package com.pios.service;

import com.pios.service.llm.LlmCredentials;
import com.pios.service.llm.LlmCredentialsResolver;
import com.pios.service.llm.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final LlmCredentialsResolver credentialsResolver;
    private final LlmProperties llmProperties;

    public EmbeddingService(@Qualifier("llmRestTemplate") RestTemplate restTemplate,
                            JdbcTemplate jdbcTemplate,
                            LlmCredentialsResolver credentialsResolver,
                            LlmProperties llmProperties) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.credentialsResolver = credentialsResolver;
        this.llmProperties = llmProperties;
    }

    public record SimilarInsight(Long insightId, String title, String summary, String feedbackStatus, double distance) {}

    /**
     * 텍스트 임베딩. 자격증명이 없거나 실패하면 null (호출 측은 임베딩 없이 진행).
     */
    public float[] embed(Long userId, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Optional<LlmCredentials> credentials = credentialsResolver.resolve(userId);
        if (credentials.isEmpty()) {
            return null;
        }
        LlmCredentials creds = credentials.get();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(creds.apiKey());

            Map<String, Object> body = Map.of(
                    "model", creds.embeddingModel(),
                    "input", text
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    llmProperties.normalizedBaseUrl() + "/embeddings",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            if (response == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Number> embedding = (List<Number>) data.get(0).get("embedding");
            if (embedding == null || embedding.isEmpty()) {
                return null;
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            return vector;
        } catch (Exception e) {
            log.warn("Embedding request failed (user={}, model={}, source={}): {}",
                    userId, creds.embeddingModel(), creds.source(), e.getMessage());
            return null;
        }
    }

    public void saveQuestionEmbedding(Long questionId, float[] embedding) {
        updateEmbedding("questions", questionId, embedding);
    }

    public void saveInsightEmbedding(Long insightId, float[] embedding) {
        updateEmbedding("insights", insightId, embedding);
    }

    public List<SimilarInsight> findSimilarInsights(Long userId, float[] queryEmbedding, int limit) {
        if (queryEmbedding == null) {
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        String sql = """
                SELECT id, title, summary, feedback_status,
                       (embedding <=> ?::vector) AS distance
                FROM insights
                WHERE user_id = ?
                  AND embedding IS NOT NULL
                  AND (feedback_status IS NULL OR feedback_status <> 'WRONG')
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> new SimilarInsight(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("summary"),
                            rs.getString("feedback_status"),
                            rs.getDouble("distance")
                    ),
                    vectorLiteral, userId, vectorLiteral, limit
            );
        } catch (Exception e) {
            log.warn("Similar insight search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private void updateEmbedding(String table, Long id, float[] embedding) {
        if (id == null || embedding == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE " + table + " SET embedding = ?::vector WHERE id = ?",
                    toVectorLiteral(embedding), id
            );
        } catch (Exception e) {
            log.warn("Failed to save embedding for {}.{}: {}", table, id, e.getMessage());
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public String formatSimilarInsightsForPrompt(List<SimilarInsight> similar) {
        if (similar == null || similar.isEmpty()) {
            return "";
        }
        return similar.stream()
                .map(s -> {
                    String status = s.feedbackStatus() != null ? " [" + s.feedbackStatus() + "]" : "";
                    String summary = s.summary() != null && s.summary().length() > 200
                            ? s.summary().substring(0, 200) + "…"
                            : s.summary();
                    return "- (" + s.insightId() + ")" + status + " " + s.title() + ": " + summary;
                })
                .collect(Collectors.joining("\n"));
    }
}
