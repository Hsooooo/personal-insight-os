package com.pios.service.briefing;

import com.pios.domain.Goal;
import com.pios.domain.Insight;
import com.pios.domain.InsightEvidence;
import com.pios.domain.User;
import com.pios.dto.AskPeriod;
import com.pios.dto.EvidenceData;
import com.pios.dto.InsightDto;
import com.pios.repository.GoalRepository;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import com.pios.repository.UserRepository;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.EvidenceStatisticsCalculator;
import com.pios.service.ask.MetricStatistic;
import com.pios.service.goal.GoalProgressCalculator;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBriefingService {

    public static final String CATEGORY = "WEEKLY_BRIEFING";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EvidenceStatisticsCalculator statisticsCalculator;
    private final GoalRepository goalRepo;
    private final GoalProgressCalculator progressCalculator;
    private final ThinPatternDetector patternDetector;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final UserRepository userRepo;
    private final RestTemplate restTemplate;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    public InsightDto getLatest(Long userId) {
        return insightRepo.findFirstByUserIdAndCategoryOrderByCreatedAtDesc(userId, CATEGORY)
                .map(this::toDtoWithEvidences)
                .orElse(null);
    }

    @Transactional
    public InsightDto generate(Long userId, boolean force) {
        AskPeriod period = lastWeekPeriod();
        String weekLabel = weekLabel(period.getStart());
        String title = weekLabel + " 주간 브리핑";

        var existing = insightRepo.findByUserIdAndCategory(userId, CATEGORY).stream()
                .filter(i -> title.equals(i.getTitle()))
                .toList();

        if (!force && !existing.isEmpty()) {
            return toDtoWithEvidences(existing.get(0));
        }

        if (force) {
            for (Insight old : existing) {
                evidenceRepo.deleteByInsightId(old.getId());
                insightRepo.delete(old);
            }
        }

        EvidenceStatistics stats = statisticsCalculator.calculate(userId, period);
        List<Goal> activeGoals = goalRepo.findByUserId(userId).stream()
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .toList();
        List<GoalProgressSnapshot> goalSnapshots = activeGoals.stream()
                .map(g -> {
                    var progress = progressCalculator.calculate(g);
                    return new GoalProgressSnapshot(g, progress);
                })
                .toList();
        List<DetectedPattern> patterns = patternDetector.detect(userId, stats, period.getEnd());

        String summary = callLlmOrFallback(period, weekLabel, stats, goalSnapshots, patterns);
        BigDecimal confidence = computeConfidence(stats);

        Insight insight = insightRepo.save(Insight.builder()
                .user(User.builder().id(userId).build())
                .question(null)
                .category(CATEGORY)
                .title(title)
                .summary(summary)
                .confidence(confidence)
                .modelProvider(openaiApiKey != null && !openaiApiKey.isBlank() ? "OpenAI" : "Fallback")
                .modelName(openaiModel)
                .isSaved(true)
                .build());

        saveEvidences(insight, stats, goalSnapshots, patterns, period);
        return toDtoWithEvidences(insight);
    }

    @Transactional
    public int generateForAllUsers() {
        int count = 0;
        for (User user : userRepo.findAll()) {
            try {
                generate(user.getId(), false);
                count++;
            } catch (Exception e) {
                log.error("Weekly briefing failed for user {}", user.getId(), e);
            }
        }
        return count;
    }

    private AskPeriod lastWeekPeriod() {
        LocalDate today = LocalDate.now(KST);
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate lastSunday = lastMonday.plusDays(6);
        LocalDate baselineEnd = lastMonday.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(27);
        return AskPeriod.builder()
                .start(lastMonday)
                .end(lastSunday)
                .baselineStart(baselineStart)
                .baselineEnd(baselineEnd)
                .build();
    }

    private String weekLabel(LocalDate weekStart) {
        int year = weekStart.get(IsoFields.WEEK_BASED_YEAR);
        int week = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    private void saveEvidences(Insight insight, EvidenceStatistics stats,
                               List<GoalProgressSnapshot> goals, List<DetectedPattern> patterns,
                               AskPeriod period) {
        String periodLabel = period.getStart() + " ~ " + period.getEnd();

        for (MetricStatistic m : topMetrics(stats, 6)) {
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType("STATS")
                    .sourceTable(null)
                    .sourceId(m.getSourceId())
                    .evidenceSummary(formatMetricSummary(m))
                    .weight(new BigDecimal("0.5"))
                    .evidenceData(EvidenceData.builder()
                            .metric(m.getLabel())
                            .currentValue(m.getCurrentValue())
                            .baselineValue(m.getBaselineValue())
                            .changeRate(m.getChangeRate())
                            .unit(m.getUnit())
                            .date(periodLabel)
                            .build())
                    .build());
        }

        for (GoalProgressSnapshot snap : goals) {
            Goal g = snap.goal();
            var p = snap.progress();
            String summary = g.getTitle() + ": "
                    + (p.getCurrentValue() != null ? p.getCurrentValue() : "-")
                    + (g.getTargetUnit() != null ? g.getTargetUnit() : "")
                    + " / " + g.getTargetValue()
                    + (p.getProgressPercent() != null ? " (" + p.getProgressPercent() + "%)" : "")
                    + " [" + (p.getPaceStatus() != null ? p.getPaceStatus() : "-") + "]";
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType("GOAL_PROGRESS")
                    .sourceTable("goals")
                    .sourceId(g.getId())
                    .evidenceSummary(summary)
                    .weight(new BigDecimal("0.6"))
                    .evidenceData(EvidenceData.builder()
                            .metric(g.getTitle())
                            .currentValue(p.getCurrentValue())
                            .baselineValue(g.getTargetValue())
                            .changeRate(p.getProgressPercent())
                            .unit(g.getTargetUnit())
                            .date(periodLabel)
                            .route(p.getPaceStatus())
                            .build())
                    .build());
        }

        for (DetectedPattern pat : patterns) {
            evidenceRepo.save(InsightEvidence.builder()
                    .insight(insight)
                    .evidenceType("PATTERN")
                    .sourceTable(null)
                    .sourceId(null)
                    .evidenceSummary("[" + pat.getSeverity() + "] " + pat.getHumanHint())
                    .weight(new BigDecimal("0.7"))
                    .evidenceData(EvidenceData.builder()
                            .metric(pat.getCode())
                            .currentValue(pat.getCurrentValue())
                            .baselineValue(pat.getBaselineValue())
                            .changeRate(pat.getChangeRate())
                            .unit(pat.getUnit())
                            .date(periodLabel)
                            .route(pat.getSeverity())
                            .build())
                    .build());
        }
    }

    private List<MetricStatistic> topMetrics(EvidenceStatistics stats, int limit) {
        return Stream.of(stats.getHealthMetrics(), stats.getSleepMetrics(), stats.getActivityMetrics())
                .flatMap(List::stream)
                .filter(m -> m.getCurrentValue() != null)
                .sorted((a, b) -> {
                    BigDecimal ca = a.getChangeRate() != null ? a.getChangeRate().abs() : BigDecimal.ZERO;
                    BigDecimal cb = b.getChangeRate() != null ? b.getChangeRate().abs() : BigDecimal.ZERO;
                    return cb.compareTo(ca);
                })
                .limit(limit)
                .toList();
    }

    private String formatMetricSummary(MetricStatistic m) {
        String change = m.getChangeRate() != null
                ? String.format(" (기준선 대비 %+.0f%%)", m.getChangeRate())
                : "";
        return m.getLabel() + ": " + m.getCurrentValue() + m.getUnit() + change;
    }

    private String callLlmOrFallback(AskPeriod period, String weekLabel, EvidenceStatistics stats,
                                     List<GoalProgressSnapshot> goals, List<DetectedPattern> patterns) {
        String context = buildContext(period, weekLabel, stats, goals, patterns);
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return buildFallback(weekLabel, stats, goals, patterns);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            String systemPrompt = """
                    당신은 Personal Insight OS의 주간 브리핑 코치입니다.
                    제공된 통계·목표·패턴만 근거로 한국어 주간 브리핑을 작성하세요.

                    규칙:
                    1. 근거에 없는 수치나 추측을 만들지 마세요.
                    2. 구성: (1) 한 줄 요약 (2) 운동·수면·회복 변화 (3) 목표 진행 (4) 주의할 패턴 (5) 다음 주 행동 1~3개
                    6. 행동은 구체적이고 실행 가능하게 쓰세요.
                    7. 300~500자 분량으로 간결하게 작성하세요.
                    """;

            Map<String, Object> body = Map.of(
                    "model", openaiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", context)
                    ),
                    "max_tokens", 700,
                    "temperature", 0.3
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
            log.warn("Weekly briefing LLM failed, using fallback", e);
        }
        return buildFallback(weekLabel, stats, goals, patterns);
    }

    private String buildContext(AskPeriod period, String weekLabel, EvidenceStatistics stats,
                                List<GoalProgressSnapshot> goals, List<DetectedPattern> patterns) {
        StringBuilder sb = new StringBuilder();
        sb.append("주간: ").append(weekLabel)
                .append(" (").append(period.getStart()).append(" ~ ").append(period.getEnd()).append(")\n");
        sb.append("기준선: ").append(period.getBaselineStart()).append(" ~ ").append(period.getBaselineEnd()).append("\n\n");

        sb.append("[통계]\n");
        for (MetricStatistic m : topMetrics(stats, 8)) {
            sb.append("- ").append(formatMetricSummary(m)).append("\n");
        }

        sb.append("\n[목표]\n");
        if (goals.isEmpty()) {
            sb.append("- 활성 목표 없음\n");
        } else {
            for (GoalProgressSnapshot snap : goals) {
                var p = snap.progress();
                sb.append("- ").append(snap.goal().getTitle())
                        .append(": ").append(p.getCurrentValue()).append("/")
                        .append(snap.goal().getTargetValue())
                        .append(" ").append(snap.goal().getTargetUnit() != null ? snap.goal().getTargetUnit() : "")
                        .append(" (").append(p.getProgressPercent()).append("%, ")
                        .append(p.getPaceStatus()).append(")\n");
            }
        }

        sb.append("\n[패턴]\n");
        if (patterns.isEmpty()) {
            sb.append("- 특이 패턴 없음\n");
        } else {
            for (DetectedPattern p : patterns) {
                sb.append("- [").append(p.getSeverity()).append("] ").append(p.getHumanHint()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildFallback(String weekLabel, EvidenceStatistics stats,
                                 List<GoalProgressSnapshot> goals, List<DetectedPattern> patterns) {
        StringBuilder sb = new StringBuilder();
        sb.append(weekLabel).append(" 주간 브리핑\n\n");

        MetricStatistic distance = findMetric(stats, "totalDistanceMeters").orElse(null);
        MetricStatistic sleep = findMetric(stats, "totalSleepSeconds").orElse(null);
        MetricStatistic sleepScore = findMetric(stats, "sleepScore").orElse(null);
        MetricStatistic activities = findMetric(stats, "activityCount").orElse(null);

        sb.append("지난주 요약\n");
        if (activities != null && activities.getCurrentValue() != null) {
            sb.append("- 운동: ").append(activities.getCurrentValue().intValue()).append("회");
            if (distance != null && distance.getCurrentValue() != null) {
                sb.append(" / ").append(distance.getCurrentValue()).append("km");
            }
            if (activities.getChangeRate() != null) {
                sb.append(String.format(" (기준선 대비 %+.0f%%)", activities.getChangeRate()));
            }
            sb.append("\n");
        }
        if (sleep != null && sleep.getCurrentValue() != null) {
            sb.append("- 수면: 평균 ").append(sleep.getCurrentValue()).append("h");
            if (sleepScore != null && sleepScore.getCurrentValue() != null) {
                sb.append(", 점수 ").append(sleepScore.getCurrentValue());
            }
            sb.append("\n");
        }

        long onTrack = goals.stream()
                .filter(g -> "ON_TRACK".equals(g.progress().getPaceStatus())
                        || "AHEAD".equals(g.progress().getPaceStatus()))
                .count();
        if (!goals.isEmpty()) {
            sb.append("- 목표: ").append(onTrack).append("/").append(goals.size()).append("개 순항\n");
        }

        if (!patterns.isEmpty()) {
            sb.append("- 주의: ").append(patterns.get(0).getHumanHint()).append("\n");
        }

        sb.append("\n다음 주 행동\n");
        List<String> actions = new ArrayList<>();
        boolean sleepIssue = patterns.stream().anyMatch(p ->
                "SLEEP_SCORE_DROP".equals(p.getCode()) || "LOW_SLEEP_STREAK".equals(p.getCode()));
        if (sleepIssue) {
            actions.add("취침 시간을 30분 앞당기고 수면 6.5시간 이상을 목표로 하세요.");
        }
        boolean volumeIssue = patterns.stream().anyMatch(p -> "VOLUME_SPIKE".equals(p.getCode()))
                || goals.stream().anyMatch(g -> "OVERTRAINING_HINT".equals(g.progress().getWarning()));
        if (volumeIssue) {
            actions.add("고강도 세션은 1회로 제한하고 회복 조깅·휴식을 넣으세요.");
        }
        goals.stream()
                .filter(g -> "BEHIND".equals(g.progress().getPaceStatus()))
                .findFirst()
                .ifPresent(g -> actions.add("「" + g.goal().getTitle() + "」 목표를 위해 이번 주 세션을 1회 추가하세요."));
        if (actions.isEmpty()) {
            actions.add("현재 페이스를 유지하며 수면·회복 지표를 매일 확인하세요.");
            actions.add("장거리 1회와 회복일 1일을 일정에 고정하세요.");
        }
        int i = 1;
        for (String action : actions.stream().limit(3).toList()) {
            sb.append(i++).append(". ").append(action).append("\n");
        }
        return sb.toString().trim();
    }

    private java.util.Optional<MetricStatistic> findMetric(EvidenceStatistics stats, String metric) {
        return Stream.of(stats.getHealthMetrics(), stats.getSleepMetrics(), stats.getActivityMetrics())
                .flatMap(List::stream)
                .filter(m -> metric.equals(m.getMetric()))
                .findFirst();
    }

    private BigDecimal computeConfidence(EvidenceStatistics stats) {
        long reliable = Stream.of(stats.getHealthMetrics(), stats.getSleepMetrics(), stats.getActivityMetrics())
                .flatMap(List::stream)
                .filter(MetricStatistic::isReliable)
                .count();
        long total = Stream.of(stats.getHealthMetrics(), stats.getSleepMetrics(), stats.getActivityMetrics())
                .mapToLong(List::size)
                .sum();
        if (total == 0) {
            return new BigDecimal("0.30");
        }
        double ratio = reliable / (double) total;
        return BigDecimal.valueOf(Math.min(0.95, 0.40 + ratio * 0.50)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private InsightDto toDtoWithEvidences(Insight insight) {
        var evidences = evidenceRepo.findByInsightId(insight.getId()).stream()
                .map(e -> com.pios.dto.EvidenceDto.builder()
                        .id(e.getId())
                        .evidenceType(e.getEvidenceType())
                        .sourceTable(e.getSourceTable())
                        .sourceId(e.getSourceId())
                        .evidenceSummary(e.getEvidenceSummary())
                        .weight(e.getWeight())
                        .evidenceData(e.getEvidenceData())
                        .build())
                .collect(Collectors.toList());
        return InsightDto.builder()
                .id(insight.getId())
                .category(insight.getCategory())
                .title(insight.getTitle())
                .summary(insight.getSummary())
                .confidence(insight.getConfidence())
                .modelProvider(insight.getModelProvider())
                .modelName(insight.getModelName())
                .feedbackStatus(insight.getFeedbackStatus())
                .isSaved(insight.getIsSaved())
                .createdAt(insight.getCreatedAt())
                .evidences(evidences)
                .build();
    }

    private record GoalProgressSnapshot(Goal goal, GoalProgressCalculator.ProgressResult progress) {}
}
