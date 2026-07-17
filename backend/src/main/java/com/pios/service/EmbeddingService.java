package com.pios.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final LlmProviderService llmProviderService;

    @Value("${openai.api-key:}")
    private String globalOpenaiApiKey;

    public record SimilarInsight(Long insightId, String title, String summary, String feedbackStatus, double distance) {}

    public float[] embed(Long userId, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String apiKey = resolveApiKey(userId);
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String model = llmProviderService.resolveEmbeddingModel(userId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", text
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api.openai.com/v1/embeddings",
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
            log.warn("Embedding request failed: {}", e.getMessage());
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

    private String resolveApiKey(Long userId) {
        String userKey = llmProviderService.resolveDecryptedApiKey(userId);
        if (userKey != null && !userKey.isBlank()) {
            return userKey;
        }
        return globalOpenaiApiKey;
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
