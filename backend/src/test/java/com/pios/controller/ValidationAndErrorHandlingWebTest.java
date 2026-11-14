package com.pios.controller;

import com.pios.common.NotFoundException;
import com.pios.config.GlobalExceptionHandler;
import com.pios.repository.GoalRepository;
import com.pios.service.ActivityService;
import com.pios.service.DataSourceService;
import com.pios.service.GoalService;
import com.pios.service.GraphService;
import com.pios.service.InsightService;
import com.pios.service.alert.AnomalyAlertService;
import com.pios.service.goal.GoalBlockerAnalyzer;
import com.pios.service.goal.GoalProgressCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입력 검증/오류 매핑 슬라이스 테스트 (standalone MockMvc: 보안 필터 없이 컨트롤러 + GlobalExceptionHandler만).
 */
class ValidationAndErrorHandlingWebTest {

    private final GoalService goalService = mock(GoalService.class);
    private final DataSourceService dataSourceService = mock(DataSourceService.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final InsightService insightService = mock(InsightService.class);
    private final GraphService graphService = mock(GraphService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(
                        new GoalController(goalService),
                        new DataSourceController(dataSourceService),
                        new ActivityController(activityService),
                        new InsightController(insightService, mock(AnomalyAlertService.class)),
                        new GraphController(graphService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createGoalWithoutTitleReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalType\":\"WEEKLY_RUN_DISTANCE\",\"targetValue\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.targetValue").exists());
        verifyNoInteractions(goalService);
    }

    @Test
    void patchGoalAllowsPartialBody() throws Exception {
        mvc.perform(patch("/api/goals/3").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        verify(goalService).updateGoal(eq(7L), eq(3L), any());
    }

    @Test
    void garminConnectRequiresValidEmailAndPassword() throws Exception {
        mvc.perform(post("/api/data-sources/garmin/connect").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
        verifyNoInteractions(dataSourceService);
    }

    @Test
    void invalidSyncTypeIsRejected() throws Exception {
        mvc.perform(post("/api/data-sources/garmin/sync").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"syncType\":\"EVERYTHING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.syncType").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/goals").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Malformed")));
    }

    @Test
    void pathVariableTypeMismatchReturns400() throws Exception {
        mvc.perform(get("/api/goals/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.goalId").exists());
    }

    @Test
    void weightTrainingSetsAreValidated() throws Exception {
        mvc.perform(post("/api/activities/weight").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2026-01-01T10:00:00\",\"exercises\":[{\"name\":\"\",\"sets\":[{\"reps\":-3}]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data['exercises[0].name']").exists())
                .andExpect(jsonPath("$.data['exercises[0].sets[0].reps']").exists());
    }

    @Test
    void requestParamConstraintsAreEnforced() throws Exception {
        mvc.perform(get("/api/insights").param("size", "100000"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/graph").param("days", "-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void insightListPassesPaging() throws Exception {
        mvc.perform(get("/api/insights").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(insightService).getInsights(7L, null, null, 2, 10);
    }

    @Test
    void notFoundIsMappedTo404() throws Exception {
        when(goalService.getGoal(7L, 99L)).thenThrow(new NotFoundException("Goal not found"));
        mvc.perform(get("/api/goals/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Goal not found"));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        when(goalService.getGoals(7L)).thenThrow(new IllegalStateException("db password=secret"));
        mvc.perform(get("/api/goals"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void goalOwnershipUsesUserScopedLookup() {
        GoalRepository repo = mock(GoalRepository.class);
        when(repo.findByIdAndUserId(5L, 7L)).thenReturn(Optional.empty());
        GoalService service = new GoalService(repo, mock(GoalProgressCalculator.class),
                mock(GoalBlockerAnalyzer.class), mock(com.pios.service.GraphProjectorService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getGoal(7L, 5L))
                .isInstanceOf(NotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteGoal(7L, 5L))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).findById(any());
        verify(repo, never()).delete(any());
    }
}
