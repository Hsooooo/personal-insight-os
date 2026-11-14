package com.pios.service.alert;

import com.pios.common.AppTimeZones;
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
import com.pios.service.llm.LlmClient;
import com.pios.service.llm.LlmPrompts;
import com.pios.service.llm.LlmPurpose;
import com.pios.service.pattern.DetectedPattern;
import com.pios.service.pattern.ThinPatternDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 매일 최근 3일 지표를 28일 기준선과 비교해 이상 신호를 감지하고
 * Insight(category=ANOMALY_ALERT)로 저장하는 능동 알림 서비스.
 * 감지 규칙은 {@link ThinPatternDetector}를 재사용한다.
 * <p>
 * LLM 호출은 트랜잭션 밖에서, 알림 1건(Insight+Evidence) 저장은 각각 독립 트랜잭션으로 처리한다.
 * 전체 사용자 실행 시 한 사용자의 실패는 다른 사용자에게 영향을 주지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyAlertService {

    public static final String CATEGORY = "ANOMALY_ALERT";
    private static final int CURRENT_DAYS = 3;
    private static final int BASELINE_DAYS = 28;
    private static final int DEDUP_DAYS = 3;
    private static final int MAX_ALERTS_PER_RUN = 2;

    private final EvidenceStatisticsCalculator statisticsCalculator;
    private final ThinPatternDetector patternDetector;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final UserRepository userRepo;
    private final GraphProjectorService graphProjector;
    private final FeedbackLearningService feedbackLearningService;
    private final LlmClient llmClient;
    private final TransactionTemplate transactionTemplate;

    public List<InsightDto> detectAndAlert(Long userId) {
        LocalDate today = AppTimeZones.todayKst();
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

    /**
     * 전체 사용자 감지. 사용자별로 격리되어 한 사용자의 실패가 나머지를 막지 않는다.
     */
    public int detectForAllUsers() {
        return detectForUsers(userRepo.findAll().stream().map(User::getId).toList());
    }

    public int detectForUsers(List<Long> userIds) {
        int alerts = 0;
        int failures = 0;
        for (Long userId : userIds) {
            try {
                alerts += detectAndAlert(userId).size();
            } catch (Exception e) {
                failures++;
                log.error("Anomaly detection failed for user {}", userId, e);
            }
        }
        if (failures > 0) {
            log.warn("Anomaly detection finished with {} failed user(s) out of {}", failures, userIds.size());
        }
        return alerts;
    }

    private InsightDto createAlert(Long userId, DetectedPattern pattern, String title,
                                   AskPeriod period, String feedbackGuidance) {
        // 1. LLM (트랜잭션 밖)
        Optional<LlmClient.ChatResult> llm = llmClient.chat(userId, LlmPurpose.ANOMALY_ALERT,
                LlmPrompts.withFeedbackGuidance(LlmPrompts.ANOMALY_ALERT_SYSTEM, feedbackGuidance),
                buildContext(pattern, period));
        String summary = llm.map(LlmClient.ChatResult::content).orElseGet(() -> buildFallback(pattern));

        // 2. 저장 (알림 단위 트랜잭션)
        record Saved(Insight insight, InsightEvidence evidence) {}
        Saved saved = transactionTemplate.execute(status -> {
            Insight insight = insightRepo.save(Insight.builder()
                    .user(User.builder().id(userId).build())
                    .question(null)
                    .category(CATEGORY)
                    .title(title)
                    .summary(summary)
                    .confidence(confidenceOf(pattern))
                    .modelProvider(llm.map(LlmClient.ChatResult::provider).orElse("Fallback"))
                    .modelName(llm.map(LlmClient.ChatResult::model).orElse(llmClient.defaultModel()))
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
            return new Saved(insight, evidence);
        });
        Insight insight = saved.insight();
        InsightEvidence evidence = saved.evidence();

        // 3. 그래프 투영 (실패해도 무시)
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

    private String buildContext(DetectedPattern pattern, AskPeriod period) {
        String unit = pattern.getUnit() != null ? pattern.getUnit() : "";
        return String.format("""
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
                pattern.getCurrentValue(), unit,
                pattern.getBaselineValue(), unit,
                pattern.getChangeRate());
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
