package com.pios.service;

import com.pios.domain.Activity;
import com.pios.domain.Insight;
import com.pios.domain.InsightEvidence;
import com.pios.domain.Question;
import com.pios.domain.User;
import com.pios.dto.AskRequest;
import com.pios.dto.AskResponse;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminActivityLapRepository;
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import com.pios.repository.QuestionRepository;
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
import java.util.List;
import java.util.Map;

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

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Transactional
    public AskResponse ask(Long userId, AskRequest request) {
        // 1. 질문 저장
        Question question = Question.builder()
                .user(User.builder().id(userId).build())
                .questionText(request.getQuestion())
                .timeRangeStart(LocalDate.now().minusDays(30))
                .timeRangeEnd(LocalDate.now())
                .build();
        question = questionRepo.save(question);

        boolean isWorkoutSummary = isWorkoutSummaryQuestion(request.getQuestion());

        // 2. 데이터 수집
        LocalDate today = LocalDate.now();
        var recentHealth = healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
                userId, today.minusDays(7), today);
        var recentSleep = sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(
                userId, today.minusDays(7), today);
        var recentActivities = activityRepo.findRecentByUserId(userId,
                today.minusDays(7).atStartOfDay());

        if (isWorkoutSummary) {
            return handleWorkoutSummary(userId, question, request, recentActivities);
        }

        // 3. 태그 기반 retrieval
        List<Activity> taggedActivities = retrieveByTag(userId, request.getQuestion());

        // 4. LLM 호출 (OpenAI)
        String conclusion = callLlm(request.getQuestion(), recentHealth, recentSleep, recentActivities, taggedActivities);

        // 5. 인사이트 저장
        Insight insight = Insight.builder()
                .user(User.builder().id(userId).build())
                .question(question)
                .category("CONDITION_ANALYSIS")
                .title("Q: " + request.getQuestion())
                .summary(conclusion)
                .confidence(new BigDecimal("0.75"))
                .modelProvider("OpenAI")
                .modelName(openaiModel)
                .isSaved(false)
                .build();
        insight = insightRepo.save(insight);

        // 6. 근거 저장
        for (var h : recentHealth) {
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType("HEALTH_METRIC")
                    .sourceTable("garmin_daily_health_metrics")
                    .sourceId(h.getId())
                    .evidenceSummary("RHR: " + h.getRestingHeartRate() + ", Stress: " + h.getStressAvg())
                    .weight(new BigDecimal("0.5"))
                    .build());
        }
        // 태그 기반 활동도 evidence로 저장
        for (var a : taggedActivities) {
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType("ACTIVITY")
                    .sourceTable("activities")
                    .sourceId(a.getId())
                    .evidenceSummary(a.getActivityName() + " (" + a.getUserTag() + "), " +
                            String.format("%.2fkm", a.getDistanceMeters() != null ? a.getDistanceMeters().doubleValue() / 1000 : 0))
                    .weight(new BigDecimal("0.6"))
                    .build());
        }

        return AskResponse.builder()
                .questionId(question.getId())
                .insightId(insight.getId())
                .conclusion(conclusion)
                .evidenceSummary(buildEvidenceSummary(recentHealth, recentSleep, recentActivities, taggedActivities))
                .relatedData(List.of())
                .confidence("중간")
                .followUpQuestion("최근 업무 스트레스나 주관적 피로 기록이 있다면 함께 분석할 수 있습니다.")
                .build();
    }

    private List<String> buildEvidenceSummary(List<?> health, List<?> sleep, List<Activity> activities, List<Activity> tagged) {
        List<String> summary = new java.util.ArrayList<>();
        summary.add("최근 7일 데이터 기준");
        summary.add("건강 지표 " + health.size() + "일");
        summary.add("수면 기록 " + sleep.size() + "일");
        summary.add("활동 기록 " + activities.size() + "개");
        if (!tagged.isEmpty()) {
            summary.add("관련 태그 활동 " + tagged.size() + "개 (" + tagged.get(0).getUserTag() + ")");
        }
        return summary;
    }

    private List<Activity> retrieveByTag(Long userId, String question) {
        Map<String, String> tagKeywords = Map.of(
                "5K", "5K / 레이스",
                "10K", "10K / 레이스",
                "하프", "하프 / 레이스",
                "풀", "풀 / 레이스"
        );
        String lowered = question.toLowerCase();
        for (var entry : tagKeywords.entrySet()) {
            if (lowered.contains(entry.getKey().toLowerCase())) {
                return activityRepo.findByUserIdAndUserTagOrderByStartTimeDesc(userId, entry.getValue());
            }
        }
        return List.of();
    }

    private String formatActivities(List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return "활동 기록 없음\n";
        }
        StringBuilder sb = new StringBuilder("활동 기록 (" + activities.size() + "개):\n");
        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            double km = a.getDistanceMeters() != null ? a.getDistanceMeters().doubleValue() / 1000.0 : 0.0;
            String date = a.getStartTime() != null ? a.getStartTime().toLocalDate().toString() : "";
            String dur = formatDuration(a.getDurationSeconds());
            String tag = a.getUserTag() != null ? a.getUserTag() : "태그 없음";
            sb.append(String.format("%d. %s (%s) — %s, %.2fkm, %s, 태그: %s%n",
                    i + 1,
                    a.getActivityName() != null ? a.getActivityName() : "활동",
                    date,
                    a.getActivityType() != null ? a.getActivityType() : "UNKNOWN",
                    km,
                    dur,
                    tag
            ));
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

    private String callLlm(String question, List<?> health, List<?> sleep,
                           List<Activity> recentActivities, List<Activity> taggedActivities) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return generateFallbackResponse(question, health, sleep, recentActivities, taggedActivities);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            String systemPrompt = """
                당신은 Personal Insight OS의 건강/운동 데이터 분석가입니다.
                사용자의 Garmin 데이터를 기반으로 근거 있는 인사이트를 제공합니다.
                
                규칙:
                1. 실제 데이터가 없는 내용은 말하지 마세요.
                2. 인과관계를 단정하지 마세요.
                3. "가능성이 있습니다", "관련 있어 보입니다" 수준으로 표현하세요.
                4. 의료 진단처럼 보이는 표현은 금지입니다.
                5. 답변은 한국어로 400자 이내로 간결하게 작성하세요.
                """;

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append(String.format("질문: %s\n\n", question));
            userPrompt.append(String.format("최근 7일 건강 지표: %d일\n", health.size()));
            userPrompt.append(String.format("최근 7일 수면 기록: %d일\n", sleep.size()));

            if (!taggedActivities.isEmpty()) {
                userPrompt.append("\n[관련 활동 검색 결과]\n");
                userPrompt.append(formatActivities(taggedActivities));
            }

            userPrompt.append("\n[최근 7일 활동]\n");
            userPrompt.append(formatActivities(recentActivities));

            userPrompt.append("\n위 데이터를 기반으로 질문에 답변해주세요.");

            Map<String, Object> body = Map.of(
                "model", openaiModel,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt.toString())
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
            // OpenAI 실패 시 폴백
        }
        return generateFallbackResponse(question, health, sleep, recentActivities, taggedActivities);
    }

    private boolean isWorkoutSummaryQuestion(String question) {
        String lowered = question.toLowerCase();
        return lowered.contains("이번주 운등") || lowered.contains("이번 주 운등")
                || lowered.contains("운등 정리") || lowered.contains("훈련 일지")
                || lowered.contains("weekly summary") || lowered.contains("workout summary")
                || lowered.contains("수행한 운등") || lowered.contains("이번주 훈련")
                || lowered.contains("이번 주 훈련") || lowered.contains("training log");
    }

    private AskResponse handleWorkoutSummary(Long userId, Question question, AskRequest request,
                                              List<Activity> recentActivities) {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(7);

        // Collect Garmin laps
        List<Long> garminIds = recentActivities.stream()
                .filter(a -> "GARMIN".equals(a.getSourceType()))
                .map(Activity::getId)
                .toList();
        Map<Long, List<com.pios.domain.GarminActivityLap>> lapsByActivity = new java.util.HashMap<>();
        if (!garminIds.isEmpty()) {
            lapsByActivity = lapRepo.findByActivityIdIn(garminIds).stream()
                    .collect(java.util.stream.Collectors.groupingBy(l -> l.getActivity().getId()));
        }

        String dataContext = buildWorkoutSummaryContext(recentActivities, lapsByActivity, since, today);
        String conclusion = callLlmForWorkoutSummary(request.getQuestion(), dataContext);

        Insight insight = Insight.builder()
                .user(User.builder().id(userId).build())
                .question(question)
                .category("WORKOUT_SUMMARY")
                .title("Q: " + request.getQuestion())
                .summary(conclusion)
                .confidence(new BigDecimal("0.85"))
                .modelProvider("OpenAI")
                .modelName(openaiModel)
                .isSaved(false)
                .build();
        insight = insightRepo.save(insight);

        for (var a : recentActivities) {
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType("ACTIVITY")
                    .sourceTable("activities")
                    .sourceId(a.getId())
                    .evidenceSummary(a.getActivityName() + " (" + a.getActivityType() + ")")
                    .weight(new BigDecimal("0.5"))
                    .build());
        }

        return AskResponse.builder()
                .questionId(question.getId())
                .insightId(insight.getId())
                .conclusion(conclusion)
                .evidenceSummary(List.of("활동 기록 " + recentActivities.size() + "개"))
                .relatedData(List.of())
                .confidence("높음")
                .followUpQuestion("특정 운동의 상세 분석이나 다음 주 훈련 계획이 필요하신가요?")
                .build();
    }

    private String buildWorkoutSummaryContext(List<Activity> activities,
                                               Map<Long, List<com.pios.domain.GarminActivityLap>> lapsByActivity,
                                               LocalDate since, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("기간: %s - %s%n%n", since, today));

        int idx = 1;
        for (Activity a : activities) {
            String date = a.getStartTime() != null ? a.getStartTime().toLocalDate().toString() : "";
            sb.append(String.format("%d. %s %s (%s)%n", idx++, date,
                    a.getActivityName() != null ? a.getActivityName() : "활동",
                    a.getActivityType() != null ? a.getActivityType() : "UNKNOWN"));

            if ("GARMIN".equals(a.getSourceType()) && lapsByActivity.containsKey(a.getId())) {
                var laps = lapsByActivity.get(a.getId());
                if (!laps.isEmpty()) {
                    sb.append("랩 | 시간 | 거리(m) | 평균심박 | 최대심박%n");
                    for (int i = 0; i < laps.size(); i++) {
                        var lap = laps.get(i);
                        sb.append(String.format("%d | %s | %s | %s | %s%n",
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
                    sb.append("종목 | 세트 | 반복 | 무게(kg) | 시간(초)%n");
                    for (Map<String, Object> ex : exercises) {
                        String exName = ex.get("name") != null ? ex.get("name").toString() : "";
                        var sets = (java.util.List<Map<String, Object>>) ex.get("sets");
                        if (sets != null) {
                            for (int i = 0; i < sets.size(); i++) {
                                var s = sets.get(i);
                                sb.append(String.format("%s | %d | %s | %s | %s%n",
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
            sb.append("%n");
        }
        return sb.toString();
    }

    private String callLlmForWorkoutSummary(String question, String dataContext) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return dataContext;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            String systemPrompt = """
                당신은 Personal Insight OS의 운동 데이터 정리 도우미입니다.
                사용자의 Garmin 활동과 수동 웨이트 트레이닝 데이터를 날짜별로 정리해주세요.
                
                규칙:
                1. Garmin 활동은 랩(lap) 단위로 표 형태로 정리하세요.
                2. 웨이트 트레이닝은 종목/세트 단위로 표 형태로 정리하세요.
                3. 각 운동 후 간단한 코멘트(강도, 특이사항 등)를 추가하세요.
                4. 마지막에 전체 주간 요약(총 활동 횟수, 총 거리, 총 볼륨 등)을 달아주세요.
                5. 답변은 한국어로 작성하세요.
                """;

            Map<String, Object> body = Map.of(
                    "model", openaiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", "질문: " + question + "\n\n" + dataContext)
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
            // OpenAI 실패 시 raw data 반환
        }
        return dataContext;
    }

    private String generateFallbackResponse(String question, List<?> health, List<?> sleep,
                                            List<Activity> activities, List<Activity> tagged) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("최근 7일간 데이터(건강 지표 %d일, 수면 %d일, 활동 %d개)를 분석한 결과, ",
                health.size(), sleep.size(), activities.size()));
        if (!tagged.isEmpty()) {
            sb.append(String.format("질문과 관련된 '%s' 태그 활동이 %d개 확인되었습니다. ",
                    tagged.get(0).getUserTag(), tagged.size()));
        }
        sb.append("질문에 대한 직접적인 인과관계를 단정하기는 어렵습니다. ");
        sb.append("다만 데이터 패턴에서 몇 가지 흥미로운 점을 찾을 수 있습니다. ");
        sb.append("LLM Provider를 설정하면 더 정교한 분석이 가능합니다.");
        return sb.toString();
    }
}
