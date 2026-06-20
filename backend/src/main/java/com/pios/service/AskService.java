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
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AskService {

    private final QuestionRepository questionRepo;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final ActivityRepository activityRepo;
    private final GarminActivityLapRepository lapRepo;
    private final RestTemplate restTemplate;
    private final EvidenceStatisticsCalculator statisticsCalculator;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Transactional
    public AskResponse ask(Long userId, AskRequest request) {
        String questionText = request.getQuestion();
        LocalDate referenceDate = AskPeriodParser.today();

        ParsedAskPeriod parsedPeriod = AskPeriodParser.parse(questionText, referenceDate);
        AskIntent intent = AskIntentClassifier.classify(questionText);

        // 1. 질문 저장
        Question question = Question.builder()
                .user(User.builder().id(userId).build())
                .questionText(questionText)
                .intent(intent.name())
                .timeRangeStart(parsedPeriod.getPeriod().getStart())
                .timeRangeEnd(parsedPeriod.getPeriod().getEnd())
                .build();
        question = questionRepo.save(question);

        // 2. 통계 계산
        EvidenceStatistics statistics = statisticsCalculator.calculate(userId, parsedPeriod.getPeriod());

        // 3. WORKOUT_SUMMARY 분기
        if (intent == AskIntent.WORKOUT_SUMMARY) {
            return handleWorkoutSummary(userId, question, questionText, parsedPeriod.getPeriod(), statistics);
        }

        // 4. 근거/신뢰도 생성
        List<AskEvidence> evidences = AskEvidenceBuilder.buildFromStatistics(statistics, intent, parsedPeriod.getPeriod());
        var confidence = ConfidenceScorer.score(parsedPeriod.getPeriod(), statistics, intent);

        // 5. LLM 호출 또는 폴린지
        String answer = callLlm(questionText, intent, parsedPeriod.getPeriod(), statistics, evidences);

        // 6. 인사이트 저장
        Insight insight = saveInsight(userId, question, intent, answer, confidence.getScore());

        // 7. 근거 저장
        saveEvidences(insight, evidences);

        return AskResponse.builder()
                .questionId(question.getId())
                .insightId(insight.getId())
                .answer(answer)
                .intent(intent.name())
                .period(parsedPeriod.getPeriod())
                .confidence(confidence)
                .evidences(evidences)
                .followUpQuestions(FollowUpQuestionGenerator.generate(intent, evidences))
                .build();
    }

    private AskResponse handleWorkoutSummary(Long userId, Question question, String questionText,
                                              AskPeriod period, EvidenceStatistics statistics) {
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
        List<AskEvidence> evidences = AskEvidenceBuilder.buildFromActivities(activities, period);
        var confidence = ConfidenceScorer.score(period, statistics, AskIntent.WORKOUT_SUMMARY);

        String answer = callLlmForWorkoutSummary(questionText, dataContext, statistics, evidences);

        Insight insight = saveInsight(userId, question, AskIntent.WORKOUT_SUMMARY, answer, confidence.getScore());
        saveEvidences(insight, evidences);

        return AskResponse.builder()
                .questionId(question.getId())
                .insightId(insight.getId())
                .answer(answer)
                .intent(AskIntent.WORKOUT_SUMMARY.name())
                .period(period)
                .confidence(confidence)
                .evidences(evidences)
                .followUpQuestions(FollowUpQuestionGenerator.generate(AskIntent.WORKOUT_SUMMARY, evidences))
                .build();
    }

    private Insight saveInsight(Long userId, Question question, AskIntent intent, String answer, BigDecimal confidence) {
        Insight insight = Insight.builder()
                .user(User.builder().id(userId).build())
                .question(question)
                .category(intent.name())
                .title("Q: " + question.getQuestionText())
                .summary(answer)
                .confidence(confidence)
                .modelProvider(openaiApiKey != null && !openaiApiKey.isBlank() ? "OpenAI" : "Fallback")
                .modelName(openaiModel)
                .isSaved(false)
                .build();
        return insightRepo.save(insight);
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

    private String callLlm(String question, AskIntent intent, com.pios.dto.AskPeriod period,
                           EvidenceStatistics statistics, List<AskEvidence> evidences) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return generateFallbackAnswer(evidences, period);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            String systemPrompt = AskPromptBuilder.buildSystemPrompt();
            String userPrompt = AskPromptBuilder.buildUserPrompt(question, intent, period, statistics, evidences);

            Map<String, Object> body = Map.of(
                    "model", openaiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", 500,
                    "temperature", 0.3
            );

            var response = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (response != null) {
                var choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    var message = (Map<String, String>) choices.get(0).get("message");
                    return message.get("content").trim();
                }
            }
        } catch (Exception e) {
            // OpenAI 실패 시 폴린지
        }
        return generateFallbackAnswer(evidences, period);
    }

    private String callLlmForWorkoutSummary(String question, String dataContext,
                                            EvidenceStatistics statistics, List<AskEvidence> evidences) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return generateWorkoutFallbackAnswer(dataContext, evidences);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            String systemPrompt = """
                    당신은 Personal Insight OS의 울등 데이터 정리 도우미입니다.
                    사용자의 Garmin 활동과 수동 웨이트 트레이닝 데이터를 날짜별로 정리해주세요.

                    규칙:
                    1. Garmin 활동은 랩(lap) 단위로 표 형태로 정리하세요.
                    2. 웨이트 트레이닝은 종목/세트 단위로 표 형태로 정리하세요.
                    3. 각 울등 후 간단한 코멘트(강도, 특이사항 등)를 추가하세요.
                    4. 마지막에 전체 주간 요약(총 활동 횟수, 총 거리, 총 볼륨 등)을 달아주세요.
                    5. 답변은 한국어로 작성하세요.
                    """;

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("질문: ").append(question).append("\n\n");
            userPrompt.append(dataContext);

            Map<String, Object> body = Map.of(
                    "model", openaiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt.toString())
                    ),
                    "max_tokens", 1500,
                    "temperature", 0.3
            );

            var response = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (response != null) {
                var choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    var message = (Map<String, String>) choices.get(0).get("message");
                    return message.get("content").trim();
                }
            }
        } catch (Exception e) {
            // OpenAI 실패 시 폴린지
        }
        return generateWorkoutFallbackAnswer(dataContext, evidences);
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
        sb.append("OpenAI Provider가 설정되지 않아 데이터 컨텍스트를 직접 정리합니다.\n\n");
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
