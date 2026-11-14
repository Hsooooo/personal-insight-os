package com.pios.service;

import com.pios.common.NotFoundException;
import com.pios.domain.Insight;
import com.pios.domain.InsightEvidence;
import com.pios.dto.EvidenceDto;
import com.pios.dto.FeedbackRequest;
import com.pios.dto.InsightDto;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightService {

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 200;

    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final GraphProjectorService graphProjector;

    /**
     * 인사이트 목록 (최신순). 응답 형태(List)는 유지하고 page/size로 잘라서 돌려준다.
     * 근거는 페이지 단위로 한 번에 조회한다 (N+1 방지).
     */
    @Transactional(readOnly = true)
    public List<InsightDto> getInsights(Long userId, String category, String feedbackStatus, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasStatus = feedbackStatus != null && !feedbackStatus.isBlank();
        List<Insight> insights;
        if (hasCategory && hasStatus) {
            insights = insightRepo.findPageByUserIdAndCategoryAndFeedbackStatus(userId, category, feedbackStatus, pageable);
        } else if (hasCategory) {
            insights = insightRepo.findPageByUserIdAndCategory(userId, category, pageable);
        } else if (hasStatus) {
            insights = insightRepo.findPageByUserIdAndFeedbackStatus(userId, feedbackStatus, pageable);
        } else {
            insights = insightRepo.findPageByUserId(userId, pageable);
        }
        return toDtos(insights);
    }

    @Transactional(readOnly = true)
    public List<InsightDto> getSavedInsights(Long userId) {
        return toDtos(insightRepo.findByUserIdAndIsSavedTrueOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public InsightDto getInsight(Long userId, Long insightId) {
        return toDtos(List.of(findOwned(userId, insightId))).get(0);
    }

    @Transactional
    public InsightDto saveInsight(Long userId, Long insightId) {
        var insight = findOwned(userId, insightId);
        insight.setIsSaved(true);
        Insight saved = insightRepo.save(insight);
        graphProjector.projectInsight(userId, saved);
        return toDtos(List.of(saved)).get(0);
    }

    @Transactional
    public InsightDto feedback(Long userId, Long insightId, FeedbackRequest request) {
        var insight = findOwned(userId, insightId);
        insight.setFeedbackStatus(request.getFeedbackStatus());
        Insight saved = insightRepo.save(insight);
        graphProjector.projectInsight(userId, saved);
        return toDtos(List.of(saved)).get(0);
    }

    @Transactional
    public void deleteInsight(Long userId, Long insightId) {
        var insight = findOwned(userId, insightId);
        insightRepo.delete(insight);
        graphProjector.deleteInsight(userId, insightId);
    }

    private Insight findOwned(Long userId, Long insightId) {
        return insightRepo.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> new NotFoundException("Insight not found"));
    }

    private List<InsightDto> toDtos(List<Insight> insights) {
        if (insights.isEmpty()) {
            return List.of();
        }
        Map<Long, List<EvidenceDto>> evidenceByInsight = evidenceRepo
                .findByInsightIdIn(insights.stream().map(Insight::getId).toList()).stream()
                .collect(Collectors.groupingBy(e -> e.getInsight().getId(),
                        Collectors.mapping(InsightService::toEvidenceDto, Collectors.toList())));
        return insights.stream()
                .map(i -> InsightDto.builder()
                        .id(i.getId()).category(i.getCategory()).title(i.getTitle())
                        .summary(i.getSummary()).confidence(i.getConfidence())
                        .modelProvider(i.getModelProvider()).modelName(i.getModelName())
                        .feedbackStatus(i.getFeedbackStatus()).isSaved(i.getIsSaved())
                        .createdAt(i.getCreatedAt())
                        .evidences(evidenceByInsight.getOrDefault(i.getId(), List.of()))
                        .build())
                .toList();
    }

    static EvidenceDto toEvidenceDto(InsightEvidence e) {
        return EvidenceDto.builder()
                .id(e.getId())
                .evidenceType(e.getEvidenceType())
                .sourceTable(e.getSourceTable())
                .sourceId(e.getSourceId())
                .evidenceSummary(e.getEvidenceSummary())
                .weight(e.getWeight())
                .evidenceData(e.getEvidenceData())
                .build();
    }
}
