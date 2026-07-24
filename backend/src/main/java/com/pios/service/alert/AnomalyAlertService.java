package com.pios.service.alert;

import com.pios.domain.Insight;
import com.pios.domain.InsightEvidence;
import com.pios.domain.User;
import com.pios.dto.AskPeriod;
import com.pios.dto.EvidenceData;
import com.pios.dto.InsightDto;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import com.pios.repository.UserRepository;
import com.pios.service.FeedbackLearningService;
import com.pios.service.GraphProjectorService;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.EvidenceStatisticsCalculator;
import com.pios.service.pattern.DetectedPattern;
import com.pios.service.pattern.ThinPatternDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 매일 최근 3일 지표를 28일 기준선과 비교해 이상 신호를 감지하고
 * Insight(category=ANOMALY_ALERT)로 저장하는 능동 알림 서비스.
 * 감지 규칙은 {@link ThinPatternDetector}를 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyAlertService {

    public static final String CATEGORY = "ANOMALY_ALERT";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CURRENT_DAYS = 3;
    private static final int BASELINE_DAYS = 28;
    private static final int DEDUP_DAYS = 3;
    private static final int MAX_ALERTS_PER_RUN = 2;

    private final EvidenceStatisticsCalculator statisticsCalculator;
    private final ThinPatternDetector patternDetector;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final UserRepository userRepo;
    private final RestTemplate restTemplate;
    private final GraphProjectorService graphProjector;
    private final FeedbackLearningService feedbackLearningService;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Transactional
    public List<InsightDto> detectAndAlert(Long userId) {
        LocalDate today = LocalDate.now(KST);
        AskPeriod period = AskPeriod.builder()
                .start(today.minusDays(CURRENT_DAYS - 1))
                .end(today)
                .baselineStart(today.minusDays(CURRENT_DAYS - 1 + BASELINE_DAYS))
                .baselineEnd(today.minusDays(CURRENT_DAYS))
                .build();

        EvidenceStatistics stats = statisticsCalculator.calculate(userId, period);
        List<DetectedPattern> patterns = patternDetector.detect(userId, stats, today);
        if (patterns.isEmpty()) {
            return List.of();
        }

        Instant dedupStart = Instant.now().minus(DEDUP_DAYS, ChronoUnit.DAYS);
        List<String> recentAlertTitles = insightRepo
                .findByUserIdAndCategoryAndCreatedAtBetween(userId, CATEGORY, dedupStart, Instant.now())
                .stream()
                .map(Insight::getTitle)
                .toList();

        String feedbackGuidance = feedbackLearningService.buildGuidance(userId);
        List<InsightDto> created = new ArrayList<>();
        for (DetectedPattern pattern : patterns) {
            if (created.size() >= MAX_ALERTS_PER_RUN) {
                break;
            }
            String title = titleOf(pattern);
            if (recentAlertTitles.contains(title)) {
                continue;
            }
            created.add(createAlert(userId, pattern, title, period, feedbackGuidance));
        }
        return created;
    }

    @Transactional
    public int detectForAllUsers() {
        int alerts = 0;
        for (User user : userRepo.findAll()) {
            try {
                alerts += detectAndAlert(user.getId()).size();
            } catch (Exception e) {
                log.error("Anomaly detection failed for user {}", user.getId(), e);
            }
        }
        return alerts;
    }

    private InsightDto createAlert(Long userId, DetectedPattern pattern, String title,
                                   AskPeriod period, String feedbackGuidance) {
        String summary = callLlmOrFallback(pattern, period, feedbackGuidance);

        Insight insight = insightRepo.save(Insight.builder()
                .user(User.builder().id(userId).build())
                .question(null)
                .category(CATEGORY)
                .title(title)
                .summary(summary)
                .confidence(confidenceOf(pattern))
                .modelProvider(openaiApiKey != null && !openaiApiKey.isBlank() ? "OpenAI" : "Fallback")
                .modelName(openaiModel)
                .isSaved(true)
                .build());

        InsightEvidence evidence = evidenceRepo.save(InsightEvidence.builder()
                .insight(insight)
                .evidenceType("PATTERN")
                .sourceTable(null)
                .sourceId(null)
                .evidenceSummary("[" + pattern.getSeverity() + "] " + pattern.getHumanHint())
                .weight(new BigDecimal("0.7"))
                .evidenceData(EvidenceData.builder()
                        .metric(pattern.getCode())
                        .currentValue(pattern.getCurrentValue())
                        .baselineValue(pattern.getBaselineValue())
                        .changeRate(pattern.getChangeRate())
                        .unit(pattern.getUnit())
                        .date(period.getStart() + " ~ " + period.getEnd())
                        .build())
                .build());

        graphProjector.projectInsight(userId, insight);
        log.info("Anomaly alert created for user {}: {}", userId, title);

        return InsightDto.builder()
                .id(insight.getId())
                .category(insight.getCategory())
                .title(insight.getTitle())
                .summary(insight.getSummary())
                .confidence(insight.getConfidence())
                .modelProvider(insight.getModelProvider())
                .modelName(insight.getModelName())
                .isSaved(insight.getIsSaved())
                .createdAt(insight.getCreatedAt())
                .evidences(List.of(com.pios.dto.EvidenceDto.builder()
                        .id(evidence.getId())
                        .evidenceType(evidence.getEvidenceType())
                        .evidenceSummary(evidence.getEvidenceSummary())
                        .weight(evidence.getWeight())
                        .evidenceData(evidence.getEvidenceData())
                        .build()))
                .build();
    }

    private String callLlmOrFallback(DetectedPattern pattern, AskPeriod period, String feedbackGuidance) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return buildFallback(pattern);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            String systemPrompt = """
                    당신은 Personal Insight OS의 생체스포츠 코치입니다.
                    사용자의 건강 데이터에서 감지된 이상 신호를 보고 먼저 말을 거는 능동 알림을 한국어로 작성하세요.

                    규칙:
                    1. 제공된 지표 수치만 근거로 사용하고, 없는 수치나 추측을 만들지 마세요.
                    2. 구성: (1) 무슨 일이 일어나고 있는지 (2) 왜 주의해야 하는지 (3) 오늘 할 수 있는 행동 1~2개
                    3. 150~250자로 간결하게, 코치가 먼저 말을 거는 자연스러운 어조로 쓰세요.
                    4. WRONG 피드백이 달린 과거 해석은 반복하지 말고, IMPORTANT는 우선 반영하세요.
                    """;
            if (feedbackGuidance != null && !feedbackGuidance.isBlank()) {
                systemPrompt = systemPrompt + "\n[사용자 피드백 학습]\n" + feedbackGuidance;
            }

            String context = String.format("""
                            기간: %s ~ %s (기준선: 직전 28일 평균)
                            감지된 신호: %s
                            심각도: %s
                            지표: %s
                            최근 값: %s%s / 기준선: %s%s / 변화율: %s%%
                            """,
                    period.getStart(), period.getEnd(),
                    pattern.getHumanHint(),
                    pattern.getSeverity(),
                    pattern.getCode(),
                    pattern.getCurrentValue(), pattern.getUnit() != null ? pattern.getUnit() : "",
                    pattern.getBaselineValue(), pattern.getUnit() != null ? pattern.getUnit() : "",
                    pattern.getChangeRate());

            Map<String, Object> body = Map.of(
                    "model", openaiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", context)
                    ),
                    "max_tokens", 400,
                    "temperature", 0.4
            );

            @SuppressWarnings("unchecked")
            var response = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            if (response != null) {
                @SuppressWarnings("unchecked")
                var choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    var message = (Map<String, String>) choices.get(0).get("message");
                    return message.get("content").trim();
                }
            }
        } catch (Exception e) {
            log.warn("Anomaly alert LLM failed, using fallback", e);
        }
        return buildFallback(pattern);
    }

    private String buildFallback(DetectedPattern pattern) {
        String advice = switch (pattern.getCode()) {
            case "SLEEP_SCORE_DROP", "LOW_SLEEP_STREAK" ->
                    "오늘은 취침 시간을 30분 앞당기고, 카페인은 오후 2시 이전에 마무리하세요.";
            case "RHR_ELEVATED" ->
                    "고강도 훈련은 쉬어가고 가벼운 산책이나 스트레칭으로 회복에 집중하세요.";
            case "VOLUME_SPIKE" ->
                    "급격한 훈련량 증가는 부상 위험이 있습니다. 오늘은 휴식 또는 회복 조깅으로 조절하세요.";
            case "BODY_BATTERY_DROP" ->
                    "몸의 에너지가 고갈되는 추세입니다. 수면 시간을 늘리고 훈련 강도를 낮추세요.";
            default -> "해당 지표를 며칠 더 관찰하며 컨디션 변화를 확인하세요.";
        };
        return pattern.getHumanHint() + ". " + advice;
    }

    private String titleOf(DetectedPattern pattern) {
        return switch (pattern.getCode()) {
            case "SLEEP_SCORE_DROP" -> "이상 신호: 수면 점수 급락";
            case "RHR_ELEVATED" -> "이상 신호: 안정시 심박 상승";
            case "VOLUME_SPIKE" -> "이상 신호: 훈련량 급증";
            case "LOW_SLEEP_STREAK" -> "이상 신호: 연속 수면 부족";
            case "BODY_BATTERY_DROP" -> "이상 신호: 바디 배터리 급락";
            default -> "이상 신호: " + pattern.getCode();
        };
    }

    private BigDecimal confidenceOf(DetectedPattern pattern) {
        return switch (pattern.getSeverity()) {
            case "HIGH" -> new BigDecimal("0.80");
            case "MED" -> new BigDecimal("0.65");
            default -> new BigDecimal("0.50");
        };
    }
}
