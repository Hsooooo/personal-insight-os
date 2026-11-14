package com.pios.service;

import com.pios.domain.Activity;
import com.pios.domain.Insight;
import com.pios.domain.InsightEvidence;
import com.pios.domain.Question;
import com.pios.domain.User;
import com.pios.dto.AskEvidence;
import com.pios.dto.AskPeriod;
import com.pios.dto.AskRequest;
import com.pios.dto.AskResponse;
import com.pios.repository.ActivityRepository;
import com.pios.repository.GarminActivityLapRepository;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import com.pios.repository.QuestionRepository;
import com.pios.service.ask.AskIntent;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.EvidenceStatisticsCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.pios.service.llm.LlmClient;
import com.pios.service.llm.LlmPurpose;
import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskServiceTest {

    @Mock
    private QuestionRepository questionRepo;
    @Mock
    private InsightRepository insightRepo;
    @Mock
    private InsightEvidenceRepository evidenceRepo;
    @Mock
    private ActivityRepository activityRepo;
    @Mock
    private GarminActivityLapRepository lapRepo;
    @Mock
    private LlmClient llmClient;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EvidenceStatisticsCalculator statisticsCalculator;
    @Mock
    private GraphProjectorService graphProjector;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private FeedbackLearningService feedbackLearningService;

    private AskService askService;

    @BeforeEach
    void setUp() {
        askService = new AskService(questionRepo, insightRepo, evidenceRepo, activityRepo, lapRepo,
                statisticsCalculator, graphProjector, embeddingService, feedbackLearningService, llmClient,
                new TransactionTemplate(transactionManager), new SyncTaskExecutor());
        lenient().when(llmClient.defaultModel()).thenReturn("gpt-4o-mini");
    }

    @Test
    void fallbackResponseHasStructuredFieldsWhenNoOpenAiKey() {
        when(questionRepo.save(any())).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(1L);
            return q;
        });
        LocalDate today = LocalDate.of(2026, 6, 20);
        AskPeriod period = AskPeriod.builder()
                .start(today.minusDays(6))
                .end(today)
                .baselineStart(today.minusDays(34))
                .baselineEnd(today.minusDays(7))
                .build();
        EvidenceStatistics stats = EvidenceStatistics.builder()
                .healthMetrics(List.of())
                .sleepMetrics(List.of())
                .activityMetrics(List.of())
                .build();
        when(statisticsCalculator.calculate(eq(1L), any())).thenReturn(stats);
        when(insightRepo.save(any())).thenAnswer(inv -> {
            Insight i = inv.getArgument(0);
            i.setId(5L);
            return i;
        });

        AskResponse response = askService.ask(1L, AskRequest.builder().question("최근 컨디션이 왜 떨어졌어").build());

        assertThat(response.getQuestionId()).isEqualTo(1L);
        assertThat(response.getInsightId()).isEqualTo(5L);
        assertThat(response.getIntent()).isEqualTo(AskIntent.CONDITION.name());
        assertThat(response.getPeriod()).isNotNull();
        assertThat(response.getConfidence()).isNotNull();
        assertThat(response.getEvidences()).isNotNull();
        assertThat(response.getFollowUpQuestions()).isNotEmpty();

        verify(questionRepo).save(any());
        verify(insightRepo).save(any());
        verify(llmClient).chat(eq(1L), eq(LlmPurpose.ASK), anyString(), anyString());
        // 부가 작업(그래프)은 응답 이후 실행되며 실패해도 영향 없음
        verify(graphProjector).projectInsight(eq(1L), any());
    }

    @Test
    void openAiSuccessReturnsLlmAnswer() {
        when(questionRepo.save(any())).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(2L);
            return q;
        });
        when(insightRepo.save(any())).thenAnswer(inv -> {
            Insight i = inv.getArgument(0);
            i.setId(6L);
            return i;
        });

        LocalDate today = LocalDate.of(2026, 6, 20);
        AskPeriod period = AskPeriod.builder()
                .start(today.minusDays(6))
                .end(today)
                .baselineStart(today.minusDays(34))
                .baselineEnd(today.minusDays(7))
                .build();
        EvidenceStatistics stats = EvidenceStatistics.builder()
                .healthMetrics(List.of(com.pios.service.ask.MetricStatistic.builder()
                        .metric("hrvAvg")
                        .label("평균 HRV")
                        .currentValue(new BigDecimal("42.00"))
                        .baselineValue(new BigDecimal("48.00"))
                        .changeRate(new BigDecimal("-12.50"))
                        .unit("ms")
                        .currentDays(7)
                        .baselineDays(28)
                        .reliable(true)
                        .sourceId(100L)
                        .sourceDate(today.toString())
                        .build()))
                .sleepMetrics(List.of())
                .activityMetrics(List.of())
                .build();
        when(statisticsCalculator.calculate(eq(1L), any())).thenReturn(stats);

        when(llmClient.chat(eq(1L), eq(LlmPurpose.ASK), anyString(), anyString()))
                .thenReturn(Optional.of(new LlmClient.ChatResult("HRV 저하와 수면 부족이 관련 있어 보입니다.", "OpenAI", "gpt-4o-mini")));

        AskResponse response = askService.ask(1L, AskRequest.builder().question("최근 컨디션이 왜 떨어졌어").build());

        assertThat(response.getAnswer()).isEqualTo("HRV 저하와 수면 부족이 관련 있어 보입니다.");
        ArgumentCaptor<Insight> insightCaptor = ArgumentCaptor.forClass(Insight.class);
        verify(insightRepo).save(insightCaptor.capture());
        assertThat(insightCaptor.getValue().getModelProvider()).isEqualTo("OpenAI");
    }

    @Test
    void evidenceDataIsSavedWithRoute() {
        when(questionRepo.save(any())).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(3L);
            return q;
        });
        when(insightRepo.save(any())).thenAnswer(inv -> {
            Insight i = inv.getArgument(0);
            i.setId(7L);
            return i;
        });

        LocalDate today = LocalDate.of(2026, 6, 20);
        EvidenceStatistics stats = EvidenceStatistics.builder()
                .healthMetrics(List.of(com.pios.service.ask.MetricStatistic.builder()
                        .metric("hrvAvg")
                        .label("평균 HRV")
                        .currentValue(new BigDecimal("42.00"))
                        .baselineValue(new BigDecimal("48.00"))
                        .changeRate(new BigDecimal("-12.50"))
                        .unit("ms")
                        .currentDays(7)
                        .baselineDays(28)
                        .reliable(true)
                        .sourceId(100L)
                        .sourceDate(today.toString())
                        .build()))
                .sleepMetrics(List.of())
                .activityMetrics(List.of())
                .build();
        when(statisticsCalculator.calculate(eq(1L), any())).thenReturn(stats);

        askService.ask(1L, AskRequest.builder().question("최근 컨디션이 왜 떨어졌어").build());

        ArgumentCaptor<InsightEvidence> captor = ArgumentCaptor.forClass(InsightEvidence.class);
        verify(evidenceRepo).save(captor.capture());
        InsightEvidence saved = captor.getValue();
        assertThat(saved.getEvidenceData()).isNotNull();
        assertThat(saved.getEvidenceData().getMetric()).isEqualTo("평균 HRV");
        assertThat(saved.getEvidenceData().getRoute()).contains("/health?date=");
    }

    @Test
    void graphAndEmbeddingFailuresDoNotFailTheRequest() {
        when(questionRepo.save(any())).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(4L);
            return q;
        });
        when(insightRepo.save(any())).thenAnswer(inv -> {
            Insight i = inv.getArgument(0);
            i.setId(8L);
            return i;
        });
        when(statisticsCalculator.calculate(eq(1L), any())).thenReturn(EvidenceStatistics.builder()
                .healthMetrics(List.of()).sleepMetrics(List.of()).activityMetrics(List.of()).build());
        when(embeddingService.embed(eq(1L), anyString())).thenThrow(new RuntimeException("embedding down"));
        org.mockito.Mockito.doThrow(new RuntimeException("neo4j down")).when(graphProjector).projectQuestion(eq(1L), any());

        AskResponse response = askService.ask(1L, AskRequest.builder().question("최근 컨디션이 왜 떨어졌어").build());

        assertThat(response.getInsightId()).isEqualTo(8L);
        assertThat(response.getAnswer()).isNotBlank();
    }
}
