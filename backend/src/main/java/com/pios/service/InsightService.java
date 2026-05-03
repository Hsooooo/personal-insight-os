package com.pios.service;

import com.pios.domain.Insight;
import com.pios.domain.User;
import com.pios.dto.FeedbackRequest;
import com.pios.dto.InsightDto;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightService {

    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;

    public List<InsightDto> getInsights(Long userId, String category, String feedbackStatus) {
        List<Insight> insights;
        if (category != null && !category.isEmpty()) {
            insights = insightRepo.findByUserIdAndCategory(userId, category);
        } else if (feedbackStatus != null && !feedbackStatus.isEmpty()) {
            insights = insightRepo.findByUserIdAndFeedbackStatus(userId, feedbackStatus);
        } else {
            insights = insightRepo.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return insights.stream().map(this::toDto).toList();
    }

    public List<InsightDto> getSavedInsights(Long userId) {
        return insightRepo.findByUserIdAndIsSavedTrueOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).toList();
    }

    public InsightDto getInsight(Long userId, Long insightId) {
        var insight = insightRepo.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        return toDto(insight);
    }

    @Transactional
    public InsightDto saveInsight(Long userId, Long insightId) {
        var insight = insightRepo.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        insight.setIsSaved(true);
        return toDto(insightRepo.save(insight));
    }

    @Transactional
    public InsightDto feedback(Long userId, Long insightId, FeedbackRequest request) {
        var insight = insightRepo.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        insight.setFeedbackStatus(request.getFeedbackStatus());
        return toDto(insightRepo.save(insight));
    }

    @Transactional
    public void deleteInsight(Long userId, Long insightId) {
        var insight = insightRepo.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        insightRepo.delete(insight);
    }

    private InsightDto toDto(Insight i) {
        var evidences = evidenceRepo.findByInsightId(i.getId()).stream()
                .map(e -> com.pios.dto.EvidenceDto.builder()
                        .id(e.getId())
                        .evidenceType(e.getEvidenceType())
                        .sourceTable(e.getSourceTable())
                        .sourceId(e.getSourceId())
                        .evidenceSummary(e.getEvidenceSummary())
                        .weight(e.getWeight())
                        .build())
                .toList();
        return InsightDto.builder()
                .id(i.getId()).category(i.getCategory()).title(i.getTitle())
                .summary(i.getSummary()).confidence(i.getConfidence())
                .modelProvider(i.getModelProvider()).modelName(i.getModelName())
                .feedbackStatus(i.getFeedbackStatus()).isSaved(i.getIsSaved())
                .createdAt(i.getCreatedAt()).evidences(evidences)
                .build();
    }
}
