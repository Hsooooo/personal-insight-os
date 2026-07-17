package com.pios.service;

import com.pios.domain.Insight;
import com.pios.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackLearningService {

    private static final int MAX_EACH = 5;

    private final InsightRepository insightRepo;

    /**
     * Builds prompt guidance from recent IMPORTANT / WRONG feedback.
     */
    public String buildGuidance(Long userId) {
        List<Insight> important = insightRepo.findByUserIdAndFeedbackStatus(userId, "IMPORTANT").stream()
                .limit(MAX_EACH)
                .toList();
        List<Insight> wrong = insightRepo.findByUserIdAndFeedbackStatus(userId, "WRONG").stream()
                .limit(MAX_EACH)
                .toList();

        if (important.isEmpty() && wrong.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (!important.isEmpty()) {
            sb.append("사용자가 IMPORTANT로 표시한 인사이트(우선 반영):\n");
            sb.append(important.stream().map(this::shortLine).collect(Collectors.joining("\n")));
            sb.append("\n");
        }
        if (!wrong.isEmpty()) {
            sb.append("사용자가 WRONG으로 표시한 인사이트(유사 해석 금지):\n");
            sb.append(wrong.stream().map(this::shortLine).collect(Collectors.joining("\n")));
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String shortLine(Insight i) {
        String summary = i.getSummary() != null && i.getSummary().length() > 160
                ? i.getSummary().substring(0, 160) + "…"
                : i.getSummary();
        return "- " + i.getTitle() + ": " + summary;
    }
}
