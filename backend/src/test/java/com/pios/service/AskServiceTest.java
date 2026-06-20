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
import com.pios.repository.GarminDailyHealthMetricRepository;
import com.pios.repository.GarminSleepSessionRepository;
import com.pios.repository.InsightEvidenceRepository;
import com.pios.repository.InsightRepository;
import com.pios.repository.QuestionRepository;
import com.pios.service.ask.AskIntent;
import com.pios.service.ask.EvidenceStatistics;
import com.pios.service.ask.EvidenceStatisticsCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private GarminDailyHealthMetricRepository healthRepo;
    @Mock
    private GarminSleepSessionRepository sleepRepo;
    @Mock
    private ActivityRepository activityRepo;
    @Mock
    private GarminActivityLapRepository lapRepo;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private EvidenceStatisticsCalculator statisticsCalculator;

    @InjectMocks
    private AskService askService;

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
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
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

        ReflectionTestUtils.setField(askService, "openaiApiKey", "test-key");
        ReflectionTestUtils.setField(askService, "openaiModel", "gpt-4o-mini");

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("choices", List.of(Map.of("message", Map.of("content", "HRV 저하와 수면 부족이 관련 있어 보입니다.")))));

        AskResponse response = askService.ask(1L, AskRequest.builder().question("최근 컨디션이 왜 떨어졌어").build());

        verify(restTemplate).postForObject(anyString(), any(), eq(Map.class));
        assertThat(response.getAnswer()).isNotBlank();
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
}
