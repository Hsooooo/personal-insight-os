package com.pios.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 그래프 투영의 MERGE 키에 대한 Neo4j 제약조건/인덱스를 기동 시 생성한다 (멱등, IF NOT EXISTS).
 * 백그라운드에서 실행하며 Neo4j가 없거나 실패해도 기동을 막지 않는다.
 * 기존 중복 데이터로 유니크 제약 생성이 실패하면 같은 키의 일반 인덱스로 대체한다.
 */
@Slf4j
@Component
public class GraphSchemaInitializer implements ApplicationRunner {

    /** label, 제약/인덱스 이름 접두사, 키 속성 */
    private record NodeKey(String label, String name, List<String> properties) {}

    private static final List<NodeKey> KEYS = List.of(
            new NodeKey("Activity", "activity", List.of("userId", "sourceId")),
            new NodeKey("HealthMetric", "health_metric", List.of("userId", "sourceId")),
            new NodeKey("Sleep", "sleep", List.of("userId", "sourceId")),
            new NodeKey("Goal", "goal", List.of("userId", "sourceId")),
            new NodeKey("Question", "question", List.of("userId", "sourceId")),
            new NodeKey("Insight", "insight", List.of("userId", "sourceId")),
            new NodeKey("Race", "race", List.of("userId", "name"))
    );

    private final Driver driver;
    private final boolean enabled;

    public GraphSchemaInitializer(Driver driver, @Value("${neo4j.init-schema:true}") boolean enabled) {
        this.driver = driver;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        // 공용 작업 풀을 점유하지 않도록 전용 데몬 스레드에서 1회 실행
        Thread thread = new Thread(this::initialize, "neo4j-schema-init");
        thread.setDaemon(true);
        thread.start();
    }

    void initialize() {
        try {
            driver.verifyConnectivity();
        } catch (Exception e) {
            log.warn("Neo4j unreachable; schema initialization skipped: {}", e.getMessage());
            return;
        }
        try (Session session = driver.session()) {
            run(session, "CREATE CONSTRAINT person_user_id IF NOT EXISTS FOR (p:Person) REQUIRE p.userId IS UNIQUE",
                    "CREATE INDEX person_user_id_idx IF NOT EXISTS FOR (p:Person) ON (p.userId)");
            for (NodeKey key : KEYS) {
                String props = String.join(", ", key.properties().stream().map(p -> "n." + p).toList());
                run(session,
                        "CREATE CONSTRAINT %s_key IF NOT EXISTS FOR (n:%s) REQUIRE (%s) IS UNIQUE"
                                .formatted(key.name(), key.label(), props),
                        "CREATE INDEX %s_key_idx IF NOT EXISTS FOR (n:%s) ON (%s)"
                                .formatted(key.name(), key.label(), props));
            }
            log.info("Neo4j schema constraints ensured");
        } catch (Exception e) {
            log.warn("Neo4j schema initialization skipped: {}", e.getMessage());
        }
    }

    private void run(Session session, String constraint, String fallbackIndex) {
        try {
            session.executeWriteWithoutResult(tx -> tx.run(constraint));
        } catch (Exception e) {
            log.warn("Neo4j constraint failed ({}), falling back to index: {}", constraint, e.getMessage());
            try {
                session.executeWriteWithoutResult(tx -> tx.run(fallbackIndex));
            } catch (Exception e2) {
                log.warn("Neo4j index creation failed ({}): {}", fallbackIndex, e2.getMessage());
            }
        }
    }
}
