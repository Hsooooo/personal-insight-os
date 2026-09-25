package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.service.GarminSyncService.TooFrequentSyncException;
import com.pios.service.GraphProjectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Duration BACKFILL_COOLDOWN = Duration.ofMinutes(5);

    private final GraphProjectorService graphProjector;
    private final TaskExecutor taskExecutor;

    // 사용자별 마지막 요청 시각 / 진행 중 여부 (단일 인스턴스 기준)
    private final Map<Long, Instant> lastRequestedAt = new ConcurrentHashMap<>();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public AdminController(GraphProjectorService graphProjector,
                           @Qualifier("syncTaskExecutor") TaskExecutor taskExecutor) {
        this.graphProjector = graphProjector;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 그래프 전체 재투영은 무거우므로 요청 스레드에서 돌리지 않고 백그라운드로 실행한다.
     */
    @PostMapping("/backfill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<String> backfill(@AuthenticationPrincipal Long userId) {
        Instant now = Instant.now();
        Instant last = lastRequestedAt.get(userId);
        if (last != null && last.plus(BACKFILL_COOLDOWN).isAfter(now)) {
            long waitSeconds = Duration.between(now, last.plus(BACKFILL_COOLDOWN)).toSeconds();
            throw new TooFrequentSyncException("Backfill was requested recently. Try again in " + waitSeconds + "s");
        }
        if (!running.add(userId)) {
            throw new TooFrequentSyncException("Backfill is already running");
        }
        lastRequestedAt.put(userId, now);

        try {
            taskExecutor.execute(() -> {
                try {
                    graphProjector.projectUserData(userId);
                    log.info("Backfill completed for user {}", userId);
                } catch (Exception e) {
                    log.error("Backfill failed for user {}", userId, e);
                } finally {
                    running.remove(userId);
                }
            });
        } catch (RuntimeException e) {
            running.remove(userId);
            throw e;
        }
        return ApiResponse.ok("Backfill started", "userId=" + userId);
    }
}
