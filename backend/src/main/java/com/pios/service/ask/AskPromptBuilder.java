package com.pios.service.ask;

import com.pios.dto.AskEvidence;
import com.pios.dto.AskPeriod;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class AskPromptBuilder {

    public static String buildSystemPrompt() {
        return """
                당신은 Personal Insight OS의 건강/울등 데이터 분석가입니다.
                사용자의 Garmin 데이터를 기반으로 근거 있는 인사이트를 제공합니다.

                규칙:
                1. 백엔드가 이미 계산한 통계와 근거만 설명하세요. 수치를 재계산하지 마세요.
                2. 실제 데이터가 없는 내용은 말하지 마세요.
                3. 인과관계를 단정하지 말고 "관련 있어 보입니다", "함께 나타나고 있습니다" 등으로 표현하세요.
                4. 의료 진단처럼 보이는 표현은 금지입니다.
                5. 답변은 한국어로 400자 이내로 간결하게 작성하세요.
                """;
    }

    public static String buildUserPrompt(String question, AskIntent intent, AskPeriod period,
                                          EvidenceStatistics statistics, List<AskEvidence> evidences) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("질문: %s\n", question));
        sb.append(String.format("의도: %s\n", intent.name()));
        sb.append(String.format("분석 기간: %s ~ %s\n", period.getStart(), period.getEnd()));
        sb.append(String.format("기준선 기간: %s ~ %s\n\n", period.getBaselineStart(), period.getBaselineEnd()));

        sb.append("[핵심 근거]\n");
        for (AskEvidence e : evidences) {
            sb.append(String.format("- %s: %s (%s)\n", e.getLabel(), e.getObservation(), e.getComparison()));
        }

        sb.append("\n[세부 통계]\n");
        appendMetrics(sb, "건강", statistics.getHealthMetrics());
        appendMetrics(sb, "수면", statistics.getSleepMetrics());
        appendMetrics(sb, "활동", statistics.getActivityMetrics());

        sb.append("\n위 근거를 바탕으로 질문에 답변해주세요.");
        return sb.toString();
    }

    private static void appendMetrics(StringBuilder sb, String category, List<MetricStatistic> metrics) {
        if (metrics == null || metrics.isEmpty()) return;
        sb.append(category).append("\n");
        for (MetricStatistic m : metrics) {
            sb.append(String.format("- %s: 현재 %s%s", m.getLabel(),
                    m.getCurrentValue() != null ? m.getCurrentValue() : "-", m.getUnit()));
            if (m.getBaselineValue() != null) {
                sb.append(String.format(" / 기준선 %s%s", m.getBaselineValue(), m.getUnit()));
            }
            if (m.getChangeRate() != null) {
                sb.append(String.format(" / 변화 %s%%", m.getChangeRate()));
            }
            sb.append(String.format(" / 데이터 %d일", m.getCurrentDays()));
            sb.append("\n");
        }
    }
}
