package com.pios.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.Instant;

/**
 * 기동 시 이전 프로세스에서 남은 RUNNING 동기화 로그를 FAILED로 정리한다.
 * (동기화는 프로세스 내 스레드풀에서만 실행되므로, 재시작 시점의 RUNNING은 모두 중단된 작업이다.
 *  단일 인스턴스 배포 전제.)
 * <p>Tomcat은 ApplicationRunner 실행 전에 요청을 받기 시작하므로, 이 프로세스가 시작된 이후에 생성된
 * RUNNING 로그(기동 직후 시작된 동기화)는 건드리지 않는다: started_at &lt; JVM 시작 시각만 정리.
 */
@Slf4j
@Component
public class SyncLogStartupSweeper implements ApplicationRunner {

    static final String MESSAGE = "Interrupted by server restart";

    @PersistenceContext
    private EntityManager entityManager;

    private final Instant processStart;

    public SyncLogStartupSweeper() {
        this(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));
    }

    SyncLogStartupSweeper(Instant processStart) {
        this.processStart = processStart;
    }

    Instant getProcessStart() {
        return processStart;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int swept = entityManager.createQuery(
                        "UPDATE SyncLog s SET s.status = 'FAILED', s.errorMessage = :message, s.completedAt = :now "
                                + "WHERE s.status = 'RUNNING' AND s.startedAt < :processStart")
                .setParameter("message", MESSAGE)
                .setParameter("now", Instant.now())
                .setParameter("processStart", processStart)
                .executeUpdate();
        entityManager.clear();
        if (swept > 0) {
            log.warn("Marked {} interrupted RUNNING sync log(s) from before {} as FAILED", swept, processStart);
        }
    }
}
