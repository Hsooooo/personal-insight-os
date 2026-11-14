package com.pios.service;

import com.pios.domain.Activity;
import com.pios.domain.GarminActivityLap;
import com.pios.domain.Insight;
import com.pios.domain.InsightEvidence;
import com.pios.domain.Question;
import com.pios.domain.User;
import com.pios.dto.AskEvidence;
import com.pios.dto.AskPeriod;
import com.pios.dto.AskRequest;
import com.pios.dto.AskResponse;
import com.pios.dto.EvidenceData;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminActivityLapRepository;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import com.pios.repository.QuestionRepository;
import com.pios.service.ask.AskEvidenceBuilder;
import com.pios.service.ask.AskIntent;
import com.pios.service.ask.AskIntentClassifier;
import com.pios.service.ask.AskPeriodParser;
import com.pios.service.ask.AskPromptBuilder;
import com.pios.service.ask.ConfidenceScorer;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.EvidenceStatisticsCalculator;
import com.pios.service.ask.FollowUpQuestionGenerator;
import com.pios.service.ask.ParsedAskPeriod;
import com.pios.service.llm.LlmClient;
import com.pios.service.llm.LlmPrompts;
import com.pios.service.llm.LlmPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ask My Data RAG 파이프라인.
 * <p>
 * DB 트랜잭션으로 HTTP 호출(임베딩/LLM)을 감싸지 않는다: 질문 저장 → (트랜잭션 밖) 통계·임베딩·LLM →
 * 인사이트+근거를 한 트랜잭션으로 저장 → 부가 작업(그래프 투영, 인사이트 임베딩)은 백그라운드에서 실패해도 무시.
 */
@Slf4j
@Service
public class AskService {

    private final QuestionRepository questionRepo;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final ActivityRepository activityRepo;
    private final GarminActivityLapRepository lapRepo;
    private final EvidenceStatisticsCalculator statisticsCalculator;
    private final GraphProjectorService graphProjector;
    private final EmbeddingService embeddingService;
    private final FeedbackLearningService feedbackLearningService;
    private final LlmClient llmClient;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor sideEffectExecutor;

    public AskService(QuestionRepository questionRepo,
                      InsightRepository insightRepo,
                      InsightEvidenceRepository evidenceRepo,
                      ActivityRepository activityRepo,
                      GarminActivityLapRepository lapRepo,
                      EvidenceStatisticsCalculator statisticsCalculator,
                      GraphProjectorService graphProjector,
                      EmbeddingService embeddingService,
                      FeedbackLearningService feedbackLearningService,
                      LlmClient llmClient,
                      TransactionTemplate transactionTemplate,
                      @Qualifier("backgroundTaskExecutor") TaskExecutor sideEffectExecutor) {
        this.questionRepo = questionRepo;
        this.insightRepo = insightRepo;
        this.evidenceRepo = evidenceRepo;
        this.activityRepo = activityRepo;
        this.lapRepo = lapRepo;
        this.statisticsCalculator = statisticsCalculator;
        this.graphProjector = graphProjector;
        this.embeddingService = embeddingService;
        this.feedbackLearningService = feedbackLearningService;
        this.llmClient = llmClient;
        this.transactionTemplate = transactionTemplate;
        this.sideEffectExecutor = sideEffectExecutor;
    }

    public AskResponse ask(Long userId, AskRequest request) {
        String questionText = request.getQuestion();
        LocalDate referenceDate = AskPeriodParser.today();

        ParsedAskPeriod parsedPeriod = AskPeriodParser.parse(questionText, referenceDate);
        AskIntent intent = AskIntentClassifier.classify(questionText);

        // 1. 질문 저장 (자체 트랜잭션)
        Question question = questionRepo.save(Question.builder()
                .user(User.builder().id(userId).build())
                .questionText(questionText)
                .intent(intent.name())
                .timeRangeStart(parsedPeriod.getPeriod().getStart())
                .timeRangeEnd(parsedPeriod.getPeriod().getEnd())
                .build());

        // 2. 유사 인사이트 검색용 임베딩 (실패 시 null → 유사 인사이트 없이 진행)
        float[] questionEmbedding = safeEmbed(userId, questionText);
        if (questionEmbedding != null) {
            embeddingService.saveQuestionEmbedding(question.getId(), questionEmbedding);
        }
        var similarInsights = embeddingService.findSimilarInsights(userId, questionEmbedding, 5);
        String similarBlock = embeddingService.formatSimilarInsightsForPrompt(similarInsights);
        String feedbackGuidance = feedbackLearningService.buildGuidance(userId);

        // 3. 통계 계산
        EvidenceStatistics statistics = statisticsCalculator.calculate(userId, parsedPeriod.getPeriod());
        AskPeriod period = parsedPeriod.getPeriod();

        List<AskEvidence> evidences;
        Answer answer;
        if (intent == AskIntent.WORKOUT_SUMMARY) {
            List<Activity> activities = activityRepo.findRecentByUserId(userId, period.getStart().atStartOfDay())
                    .stream()
                    .filter(a -> a.getStartTime() != null)
                    .filter(a -> !a.getStartTime().toLocalDate().isBefore(period.getStart())
                            && !a.getStartTime().toLocalDate().isAfter(period.getEnd()))
                    .toList();
            List<Long> garminIds = activities.stream()
                    .filter(a -> "GARMIN".equals(a.getSourceType()))
                    .map(Activity::getId)
                    .toList();
            Map<Long, List<GarminActivityLap>> lapsByActivity = new HashMap<>();
            if (!garminIds.isEmpty()) {
                lapsByActivity = lapRepo.findByActivityIdIn(garminIds).stream()
                        .collect(Collectors.groupingBy(l -> l.getActivity().getId()));
            }
            String dataContext = buildWorkoutSummaryContext(activities, lapsByActivity, period);
            evidences = AskEvidenceBuilder.buildFromActivities(activities, period);
            answer = callLlmForWorkoutSummary(userId, questionText, dataContext, evidences, feedbackGuidance);
        } else {
            evidences = AskEvidenceBuilder.buildFromStatistics(statistics, intent, period);
            answer = callLlm(userId, questionText, intent, period, statistics, evidences,
                    similarBlock, feedbackGuidance);
        }
        var confidence = ConfidenceScorer.score(period, statistics, intent);

        // 4. 인사이트 + 근거 저장 (한 트랜잭션)
        final List<AskEvidence> finalEvidences = evidences;
        Insight insight = transactionTemplate.execute(status -> {
            Insight saved = saveInsight(userId, question, intent, answer, confidence.getScore());
            saveEvidences(saved, finalEvidences);
            return saved;
        });

        // 5. 부가 작업: 그래프 투영 + 인사이트 임베딩 (실패해도 응답에 영향 없음)
        runSideEffects(userId, question, insight);

        return AskResponse.builder()
                .questionId(question.getId())
                .insightId(insight != null ? insight.getId() : null)
                .answer(answer.text())
                .intent(intent.name())
                .period(period)
                .confidence(confidence)
                .evidences(evidences)
                .followUpQuestions(FollowUpQuestionGenerator.generate(intent, evidences))
                .build();
    }

    private record Answer(String text, String provider, String model) {}

    private float[] safeEmbed(Long userId, String text) {
        try {
            return embeddingService.embed(userId, text);
        } catch (Exception e) {
            log.warn("Question embedding failed for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void runSideEffects(Long userId, Question question, Insight insight) {
        Runnable task = () -> {
            try {
                graphProjector.projectQuestion(userId, question);
                if (insight != null) {
                    graphProjector.projectInsight(userId, insight);
                    float[] embedding = embeddingService.embed(userId, insight.getTitle() + "\n" + insight.getSummary());
                    if (embedding != null) {
                        embeddingService.saveInsightEmbedding(insight.getId(), embedding);
                    }
                }
            } catch (Exception e) {
                log.warn("Ask side effects failed for user {} (question {}): {}", userId, question.getId(), e.toString());
            }
        };
        try {
            sideEffectExecutor.execute(task);
        } catch (Exception e) {
            log.warn("Ask side-effect queue rejected task for user {}: {}", userId, e.getMessage());
        }
    }

    private Insight saveInsight(Long userId, Question question, AskIntent intent, Answer answer, BigDecimal confidence) {
        Insight insight = Insight.builder()
                .user(User.builder().id(userId).build())
                .question(question)
                .category(intent.name())
                .title(truncate("Q: " + question.getQuestionText(), 255))
                .summary(answer.text())
                .confidence(confidence)
                .modelProvider(answer.provider())
                .modelName(answer.model())
                .isSaved(false)
                .build();
        return insightRepo.save(insight);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void saveEvidences(Insight insight, List<AskEvidence> evidences) {
        for (AskEvidence e : evidences) {
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType(e.getType())
                    .sourceTable(sourceTable(e.getType()))
                    .sourceId(e.getSourceId())
                    .evidenceSummary(e.getObservation() + " — " + e.getComparison())
                    .weight(new BigDecimal("0.5"))
                    .evidenceData(toEvidenceData(e))
                    .build());
        }
    }

    private EvidenceData toEvidenceData(AskEvidence e) {
        return EvidenceData.builder()
                .metric(e.getLabel())
                .currentValue(e.getCurrentValue())
                .baselineValue(e.getBaselineValue())
                .changeRate(e.getChangeRate())
                .unit(e.getUnit())
                .date(e.getSourceDate())
                .route(e.getRoute())
                .build();
    }

    private String sourceTable(String type) {
        return switch (type) {
            case "HEALTH_METRIC" -> "garmin_daily_health_metrics";
            case "SLEEP" -> "garmin_sleep_sessions";
            case "ACTIVITY" -> "activities";
            default -> null;
        };
    }

    private Answer callLlm(Long userId, String question, AskIntent intent, AskPeriod period,
                           EvidenceStatistics statistics, List<AskEvidence> evidences,
                           String similarInsightsBlock, String feedbackGuidance) {
        String systemPrompt = AskPromptBuilder.buildSystemPrompt(feedbackGuidance);
        String userPrompt = AskPromptBuilder.buildUserPrompt(
                question, intent, period, statistics, evidences, similarInsightsBlock);
        Optional<LlmClient.ChatResult> result = llmClient.chat(userId, LlmPurpose.ASK, systemPrompt, userPrompt);
        return result
                .map(r -> new Answer(r.content(), r.provider(), r.model()))
                .orElseGet(() -> new Answer(generateFallbackAnswer(evidences, period), "Fallback", llmClient.defaultModel()));
    }

    private Answer callLlmForWorkoutSummary(Long userId, String question, String dataContext,
                                            List<AskEvidence> evidences, String feedbackGuidance) {
        String systemPrompt = LlmPrompts.withFeedbackGuidance(LlmPrompts.WORKOUT_SUMMARY_SYSTEM, feedbackGuidance);
        String userPrompt = "질문: " + question + "\n\n" + dataContext;
        Optional<LlmClient.ChatResult> result =
                llmClient.chat(userId, LlmPurpose.WORKOUT_SUMMARY, systemPrompt, userPrompt);
        return result
                .map(r -> new Answer(r.content(), r.provider(), r.model()))
                .orElseGet(() -> new Answer(generateWorkoutFallbackAnswer(dataContext, evidences),
                        "Fallback", llmClient.defaultModel()));
    }

    private String generateFallbackAnswer(List<AskEvidence> evidences, com.pios.dto.AskPeriod period) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s ~ %s 데이터를 분석한 결과, ", period.getStart(), period.getEnd()));
        if (evidences.isEmpty()) {
            sb.append("해당 기간에 충분한 데이터가 없어 확실한 패턴을 찾기 어렵습니다. 더 긴 기간이나 다른 질문으로 다시 시도해주세요.");
            return sb.toString();
        }
        for (int i = 0; i < Math.min(3, evidences.size()); i++) {
            AskEvidence e = evidences.get(i);
            sb.append(String.format("%s는 %s(%s)", e.getLabel(), e.getObservation(), e.getComparison()));
            if (i < Math.min(3, evidences.size()) - 1) {
                sb.append(", ");
            }
        }
        sb.append(". ");
        sb.append("이 패턴은 개인 기준선과의 비교 결과이며, 직접적인 인과관계를 단정하기는 어렵습니다.");
        return sb.toString();
    }

    private String generateWorkoutFallbackAnswer(String dataContext, List<AskEvidence> evidences) {
        StringBuilder sb = new StringBuilder();
        sb.append("LLM 응답을 사용할 수 없어 데이터 컨텍스트를 직접 정리합니다.\n\n");
        sb.append(dataContext);
        return sb.toString();
    }

    private String buildWorkoutSummaryContext(List<Activity> activities,
                                               Map<Long, List<GarminActivityLap>> lapsByActivity,
                                               com.pios.dto.AskPeriod period) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("기간: %s - %s\n\n", period.getStart(), period.getEnd()));

        int idx = 1;
        for (Activity a : activities) {
            String date = a.getStartTime() != null ? a.getStartTime().toLocalDate().toString() : "";
            sb.append(String.format("### %d. %s %s (%s)\n", idx++, date,
                    a.getActivityName() != null ? a.getActivityName() : "활동",
                    a.getActivityType() != null ? a.getActivityType() : "UNKNOWN"));

            if ("GARMIN".equals(a.getSourceType()) && lapsByActivity.containsKey(a.getId())) {
                var laps = lapsByActivity.get(a.getId());
                if (!laps.isEmpty()) {
                    sb.append("| 랩 | 시간 | 거리(m) | 평균심박 | 최대심박 |\n");
                    sb.append("|---|---|---|---|---|\n");
                    for (int i = 0; i < laps.size(); i++) {
                        var lap = laps.get(i);
                        sb.append(String.format("| %d | %s | %s | %s | %s |\n",
                                i + 1,
                                formatDuration(lap.getDurationSeconds()),
                                lap.getDistanceMeters() != null ? lap.getDistanceMeters() : "-",
                                lap.getAverageHeartRate() != null ? lap.getAverageHeartRate() : "-",
                                lap.getMaxHeartRate() != null ? lap.getMaxHeartRate() : "-"
                        ));
                    }
                }
            } else if ("MANUAL".equals(a.getSourceType()) && a.getWeightTrainingDetail() != null) {
                var exercises = (java.util.List<Map<String, Object>>) a.getWeightTrainingDetail().get("exercises");
                if (exercises != null && !exercises.isEmpty()) {
                    sb.append("| 종목 | 세트 | 반복 | 무게(kg) | 시간(초) |\n");
                    sb.append("|---|---|---|---|---|\n");
                    for (Map<String, Object> ex : exercises) {
                        String exName = ex.get("name") != null ? ex.get("name").toString() : "";
                        var sets = (java.util.List<Map<String, Object>>) ex.get("sets");
                        if (sets != null) {
                            for (int i = 0; i < sets.size(); i++) {
                                var s = sets.get(i);
                                sb.append(String.format("| %s | %d | %s | %s | %s |\n",
                                        exName,
                                        i + 1,
                                        s.get("reps") != null ? s.get("reps") : "-",
                                        s.get("weightKg") != null ? s.get("weightKg") : "-",
                                        s.get("durationSeconds") != null ? s.get("durationSeconds") : "-"
                                ));
                            }
                        }
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return "-";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
    }
}
