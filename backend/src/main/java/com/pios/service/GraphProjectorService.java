package com.pios.service;

import com.pios.common.AppTimeZones;
import com.pios.domain.*;
import com.pios.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PostgreSQL 데이터를 Neo4j 그래프로 투영한다.
 * <ul>
 *   <li>전체 투영({@link #projectUserData})은 UNWIND 배치 Cypher + 배치 매핑 upsert로 처리한다.</li>
 *   <li>단건 투영/삭제는 호출 측의 부가 작업이므로 실패해도 예외를 던지지 않는다(로그만).
 *       DB 트랜잭션 안에서 호출되면 커밋 이후로 미뤄 실행한다(롤백된 데이터가 그래프에 남지 않도록).</li>
 * </ul>
 */
@Slf4j
@Service
public class GraphProjectorService {

    private static final int BATCH_SIZE = 500;
    private static final int PROJECTION_DAYS = 30;

    private static final String UPSERT_MAPPING_SQL = """
            INSERT INTO graph_node_mappings (user_id, source_table, source_id, neo4j_node_id, node_type)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (source_table, source_id, node_type)
            DO UPDATE SET neo4j_node_id = EXCLUDED.neo4j_node_id
            WHERE graph_node_mappings.user_id = EXCLUDED.user_id
            """;

    private static final String DELETE_MAPPING_SQL = """
            DELETE FROM graph_node_mappings
            WHERE user_id = ? AND source_table = ? AND source_id = ? AND node_type = ?
            """;

    private final Driver neo4jDriver;
    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final GoalRepository goalRepo;
    private final QuestionRepository questionRepo;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;

    public GraphProjectorService(Driver neo4jDriver,
                                 ActivityRepository activityRepo,
                                 GarminDailyHealthMetricRepository healthRepo,
                                 GarminSleepSessionRepository sleepRepo,
                                 GoalRepository goalRepo,
                                 QuestionRepository questionRepo,
                                 InsightRepository insightRepo,
                                 InsightEvidenceRepository evidenceRepo,
                                 JdbcTemplate jdbcTemplate,
                                 PlatformTransactionManager transactionManager) {
        this.neo4jDriver = neo4jDriver;
        this.activityRepo = activityRepo;
        this.healthRepo = healthRepo;
        this.sleepRepo = sleepRepo;
        this.goalRepo = goalRepo;
        this.questionRepo = questionRepo;
        this.insightRepo = insightRepo;
        this.evidenceRepo = evidenceRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // =====================================================================
    // 전체 투영 (동기화 후 / 백필). Neo4j 실패 시 예외를 던지며 호출 측이 로그 처리한다.
    // =====================================================================

    public void projectUserData(Long userId) {
        LocalDate today = AppTimeZones.todayKst();
        List<Activity> activities = activityRepo
                .findByUserIdOrderByStartTimeDesc(userId, Pageable.unpaged()).getContent();
        List<GarminDailyHealthMetric> health = healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
                userId, today.minusDays(PROJECTION_DAYS), today);
        List<GarminSleepSession> sleeps = sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(
                userId, today.minusDays(PROJECTION_DAYS), today);
        List<Goal> goals = goalRepo.findByUserId(userId);
        List<Question> questions = questionRepo.findByUserIdOrderByCreatedAtDesc(userId);
        List<Insight> insights = insightRepo.findByUserIdOrderByCreatedAtDesc(userId);

        try (Session session = neo4jDriver.session()) {
            ensurePerson(session, userId);
            writeActivities(session, userId, activities);
            writeHealthMetrics(session, userId, health);
            writeSleeps(session, userId, sleeps);
            writeGoals(session, userId, goals);
            writeQuestions(session, userId, questions);
            writeInsights(session, userId, insights);
        }
        log.info("Projected graph for user {}: activities={}, health={}, sleep={}, goals={}, questions={}, insights={}",
                userId, activities.size(), health.size(), sleeps.size(), goals.size(), questions.size(), insights.size());
    }

    // =====================================================================
    // 단건 투영/삭제 (부가 작업: 실패해도 호출 측에 영향 없음)
    // =====================================================================

    public void projectGoal(Long userId, Goal goal) {
        sideEffect("projectGoal", userId, () -> withSession(s -> writeGoals(s, userId, List.of(goal))));
    }

    public void deleteGoal(Long userId, Long goalId) {
        sideEffect("deleteGoal", userId, () -> {
            withSession(s -> s.executeWriteWithoutResult(tx -> tx.run(
                    "MATCH (g:Goal {sourceId: $sourceId, userId: $userId}) DETACH DELETE g",
                    Map.of("sourceId", goalId, "userId", userId))));
            deleteMapping(userId, "goals", goalId, "Goal");
        });
    }

    public void projectQuestion(Long userId, Question question) {
        sideEffect("projectQuestion", userId, () -> withSession(s -> writeQuestions(s, userId, List.of(question))));
    }

    public void projectInsight(Long userId, Insight insight) {
        sideEffect("projectInsight", userId, () -> withSession(s -> writeInsights(s, userId, List.of(insight))));
    }

    public void deleteInsight(Long userId, Long insightId) {
        sideEffect("deleteInsight", userId, () -> {
            withSession(s -> s.executeWriteWithoutResult(tx -> tx.run(
                    "MATCH (i:Insight {sourceId: $sourceId, userId: $userId}) DETACH DELETE i",
                    Map.of("sourceId", insightId, "userId", userId))));
            deleteMapping(userId, "insights", insightId, "Insight");
        });
    }

    public void projectActivity(Long userId, Activity a) {
        sideEffect("projectActivity", userId, () -> withSession(s -> writeActivities(s, userId, List.of(a))));
    }

    public void deleteActivity(Long userId, Long activityId) {
        sideEffect("deleteActivity", userId, () -> {
            withSession(s -> s.executeWriteWithoutResult(tx -> {
                removeRaceTag(tx, userId, activityId);
                tx.run("MATCH (a:Activity {sourceId: $sourceId, userId: $userId}) DETACH DELETE a",
                        Map.of("sourceId", activityId, "userId", userId));
            }));
            deleteMapping(userId, "activities", activityId, "Activity");
            deleteMapping(userId, "activities_race", activityId, "Race");
        });
    }

    public void updateActivityTag(Long userId, Long activityId, String newTag) {
        sideEffect("updateActivityTag", userId, () -> {
            List<NodeRef> refs = withSessionResult(s -> s.executeWrite(tx -> {
                tx.run("MATCH (a:Activity {sourceId: $sourceId, userId: $userId}) SET a.userTag = $newTag",
                        Map.of("sourceId", activityId, "userId", userId, "newTag", newTag != null ? newTag : ""));
                removeRaceTag(tx, userId, activityId);
                if (newTag == null || newTag.isBlank()) {
                    return List.<NodeRef>of();
                }
                return runReturningRefs(tx, """
                        MATCH (a:Activity {sourceId: $sourceId, userId: $userId})
                        MERGE (r:Race {userId: $userId, name: $tagName})
                        SET r.category = $category
                        MERGE (a)-[:TAGGED_AS]->(r)
                        RETURN a.sourceId AS sourceId, elementId(r) AS neoId
                        """, Map.of("sourceId", activityId, "userId", userId,
                        "tagName", newTag, "category", extractCategory(newTag)));
            }));
            if (newTag == null || newTag.isBlank()) {
                deleteMapping(userId, "activities_race", activityId, "Race");
            } else {
                upsertMappings(userId, "activities_race", "Race", refs);
            }
        });
    }

    // =====================================================================
    // 배치 쓰기 (UNWIND)
    // =====================================================================

    private void writeActivities(Session session, Long userId, List<Activity> activities) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> raceRows = new ArrayList<>();
        for (Activity a : activities) {
            if (a.getId() == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("sourceId", a.getId());
            row.put("name", a.getActivityName() != null ? a.getActivityName() : "Activity");
            row.put("type", a.getActivityType() != null ? a.getActivityType() : "UNKNOWN");
            row.put("date", a.getStartTime() != null ? a.getStartTime().toString() : "");
            row.put("distance", a.getDistanceMeters() != null ? a.getDistanceMeters().doubleValue() : 0.0);
            row.put("userTag", a.getUserTag() != null ? a.getUserTag() : "");
            row.put("sourceType", a.getSourceType() != null ? a.getSourceType() : "GARMIN");
            rows.add(row);
            if (a.getUserTag() != null && !a.getUserTag().isBlank()) {
                raceRows.add(Map.of("sourceId", a.getId(), "tag", a.getUserTag(),
                        "category", extractCategory(a.getUserTag())));
            }
        }
        writeBatched(session, userId, rows, "activities", "Activity", """
                MERGE (p:Person {userId: $userId})
                WITH p
                UNWIND $rows AS row
                MERGE (n:Activity {sourceId: row.sourceId, userId: $userId})
                SET n.name = row.name, n.type = row.type, n.date = row.date,
                    n.distance = row.distance, n.userTag = row.userTag, n.sourceType = row.sourceType
                MERGE (p)-[:PERFORMED]->(n)
                RETURN row.sourceId AS sourceId, elementId(n) AS neoId
                """);
        writeBatched(session, userId, raceRows, "activities_race", "Race", """
                UNWIND $rows AS row
                MATCH (a:Activity {sourceId: row.sourceId, userId: $userId})
                MERGE (r:Race {userId: $userId, name: row.tag})
                SET r.category = row.category
                MERGE (a)-[:TAGGED_AS]->(r)
                RETURN row.sourceId AS sourceId, elementId(r) AS neoId
                """);
    }

    private void writeHealthMetrics(Session session, Long userId, List<GarminDailyHealthMetric> metrics) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GarminDailyHealthMetric h : metrics) {
            Map<String, Object> row = new HashMap<>();
            row.put("sourceId", h.getId());
            row.put("date", h.getMetricDate().toString());
            // 값이 없으면 null → 속성 제거 (0으로 채우지 않는다)
            row.put("rhr", h.getRestingHeartRate());
            row.put("stress", h.getStressAvg() != null ? h.getStressAvg().doubleValue() : null);
            row.put("weight", h.getWeightKg() != null ? h.getWeightKg().doubleValue() : null);
            rows.add(row);
        }
        writeBatched(session, userId, rows, "garmin_daily_health_metrics", "HealthMetric", """
                MERGE (p:Person {userId: $userId})
                WITH p
                UNWIND $rows AS row
                MERGE (n:HealthMetric {sourceId: row.sourceId, userId: $userId})
                SET n.date = row.date, n.rhr = row.rhr, n.stress = row.stress, n.weight = row.weight
                MERGE (p)-[:HAS_METRIC]->(n)
                RETURN row.sourceId AS sourceId, elementId(n) AS neoId
                """);
    }

    private void writeSleeps(Session session, Long userId, List<GarminSleepSession> sleeps) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GarminSleepSession s : sleeps) {
            Map<String, Object> row = new HashMap<>();
            row.put("sourceId", s.getId());
            row.put("date", s.getSleepDate().toString());
            row.put("score", s.getSleepScore());
            row.put("duration", s.getTotalSleepSeconds());
            rows.add(row);
        }
        writeBatched(session, userId, rows, "garmin_sleep_sessions", "Sleep", """
                MERGE (p:Person {userId: $userId})
                WITH p
                UNWIND $rows AS row
                MERGE (n:Sleep {sourceId: row.sourceId, userId: $userId})
                SET n.date = row.date, n.score = row.score, n.duration = row.duration
                MERGE (p)-[:HAS_SLEEP]->(n)
                RETURN row.sourceId AS sourceId, elementId(n) AS neoId
                """);
    }

    private void writeGoals(Session session, Long userId, List<Goal> goals) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Goal g : goals) {
            if (g.getId() == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("sourceId", g.getId());
            row.put("title", g.getTitle() != null ? g.getTitle() : "Goal");
            row.put("goalType", g.getGoalType() != null ? g.getGoalType() : "");
            row.put("status", g.getStatus() != null ? g.getStatus() : "ACTIVE");
            row.put("targetValue", g.getTargetValue() != null ? g.getTargetValue().doubleValue() : 0.0);
            row.put("targetUnit", g.getTargetUnit() != null ? g.getTargetUnit() : "");
            rows.add(row);
        }
        writeBatched(session, userId, rows, "goals", "Goal", """
                MERGE (p:Person {userId: $userId})
                WITH p
                UNWIND $rows AS row
                MERGE (g:Goal {sourceId: row.sourceId, userId: $userId})
                SET g.title = row.title, g.goalType = row.goalType, g.status = row.status,
                    g.targetValue = row.targetValue, g.targetUnit = row.targetUnit
                MERGE (p)-[:HAS_GOAL]->(g)
                RETURN row.sourceId AS sourceId, elementId(g) AS neoId
                """);
    }

    private void writeQuestions(Session session, Long userId, List<Question> questions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Question q : questions) {
            if (q.getId() == null) continue;
            String text = q.getQuestionText() != null ? q.getQuestionText() : "";
            String label = text.length() > 40 ? text.substring(0, 40) + "…" : text;
            Map<String, Object> row = new HashMap<>();
            row.put("sourceId", q.getId());
            row.put("text", text);
            row.put("intent", q.getIntent() != null ? q.getIntent() : "");
            row.put("label", label.isBlank() ? "Question" : label);
            rows.add(row);
        }
        writeBatched(session, userId, rows, "questions", "Question", """
                MERGE (p:Person {userId: $userId})
                WITH p
                UNWIND $rows AS row
                MERGE (q:Question {sourceId: row.sourceId, userId: $userId})
                SET q.text = row.text, q.intent = row.intent, q.label = row.label
                MERGE (p)-[:ASKED]->(q)
                RETURN row.sourceId AS sourceId, elementId(q) AS neoId
                """);
    }

    private void writeInsights(Session session, Long userId, List<Insight> insights) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> answeredRows = new ArrayList<>();
        List<Long> insightIds = new ArrayList<>();
        for (Insight i : insights) {
            if (i.getId() == null) continue;
            insightIds.add(i.getId());
            String title = i.getTitle() != null ? i.getTitle() : "Insight";
            Map<String, Object> row = new HashMap<>();
            row.put("sourceId", i.getId());
            row.put("title", title);
            row.put("category", i.getCategory() != null ? i.getCategory() : "");
            row.put("confidence", i.getConfidence() != null ? i.getConfidence().doubleValue() : 0.0);
            row.put("isSaved", Boolean.TRUE.equals(i.getIsSaved()));
            row.put("label", title.length() > 40 ? title.substring(0, 40) + "…" : title);
            rows.add(row);
            if (i.getQuestion() != null && i.getQuestion().getId() != null) {
                answeredRows.add(Map.of("questionId", i.getQuestion().getId(), "insightId", i.getId()));
            }
        }
        writeBatched(session, userId, rows, "insights", "Insight", """
                MERGE (p:Person {userId: $userId})
                WITH p
                UNWIND $rows AS row
                MERGE (i:Insight {sourceId: row.sourceId, userId: $userId})
                SET i.title = row.title, i.category = row.category, i.confidence = row.confidence,
                    i.isSaved = row.isSaved, i.label = row.label
                MERGE (p)-[:HAS_INSIGHT]->(i)
                RETURN row.sourceId AS sourceId, elementId(i) AS neoId
                """);
        runBatched(session, userId, answeredRows, """
                UNWIND $rows AS row
                MATCH (q:Question {sourceId: row.questionId, userId: $userId})
                MATCH (i:Insight {sourceId: row.insightId, userId: $userId})
                MERGE (q)-[:ANSWERED_BY]->(i)
                """);

        // 근거 → 원천 노드 연결 (라벨별 배치)
        Map<String, List<Map<String, Object>>> linksByLabel = new HashMap<>();
        for (List<Long> chunk : partition(insightIds, BATCH_SIZE)) {
            for (InsightEvidence e : evidenceRepo.findByInsightIdIn(chunk)) {
                String label = nodeLabelFor(e.getSourceTable());
                if (label == null || e.getSourceId() == null) continue;
                linksByLabel.computeIfAbsent(label, k -> new ArrayList<>())
                        .add(Map.of("insightId", e.getInsight().getId(), "sourceId", e.getSourceId()));
            }
        }
        linksByLabel.forEach((label, links) -> runBatched(session, userId, links, """
                UNWIND $rows AS row
                MATCH (i:Insight {sourceId: row.insightId, userId: $userId})
                MATCH (n:%s {sourceId: row.sourceId, userId: $userId})
                MERGE (i)-[:DERIVED_FROM]->(n)
                MERGE (i)-[:SUPPORTED_BY]->(n)
                """.formatted(label)));
    }

    private static String nodeLabelFor(String sourceTable) {
        if (sourceTable == null) return null;
        return switch (sourceTable) {
            case "activities", "garmin_activities" -> "Activity";
            case "garmin_sleep_sessions" -> "Sleep";
            case "garmin_daily_health_metrics" -> "HealthMetric";
            default -> null;
        };
    }

    private void removeRaceTag(TransactionContext tx, Long userId, Long activityId) {
        tx.run("""
                MATCH (a:Activity {sourceId: $sourceId, userId: $userId})
                OPTIONAL MATCH (a)-[t:TAGGED_AS]->(r:Race)
                DELETE t
                WITH r
                WHERE r IS NOT NULL
                OPTIONAL MATCH (r)<-[:TAGGED_AS]-(other:Activity)
                WITH r, other
                WHERE other IS NULL
                DELETE r
                """, Map.of("sourceId", activityId, "userId", userId));
    }

    private void ensurePerson(Session session, Long userId) {
        session.executeWriteWithoutResult(tx -> tx.run(
                "MERGE (p:Person {userId: $userId}) SET p.name = 'User'", Map.of("userId", userId)));
    }

    // =====================================================================
    // 공통 헬퍼
    // =====================================================================

    private record NodeRef(Long sourceId, String neoId) {}

    private void writeBatched(Session session, Long userId, List<Map<String, Object>> rows,
                              String sourceTable, String nodeType, String cypher) {
        for (List<Map<String, Object>> chunk : partition(rows, BATCH_SIZE)) {
            List<NodeRef> refs = session.executeWrite(tx ->
                    runReturningRefs(tx, cypher, Map.of("userId", userId, "rows", chunk)));
            upsertMappings(userId, sourceTable, nodeType, refs);
        }
    }

    private void runBatched(Session session, Long userId, List<Map<String, Object>> rows, String cypher) {
        for (List<Map<String, Object>> chunk : partition(rows, BATCH_SIZE)) {
            session.executeWriteWithoutResult(tx -> tx.run(cypher, Map.of("userId", userId, "rows", chunk)));
        }
    }

    private static List<NodeRef> runReturningRefs(TransactionContext tx, String cypher, Map<String, Object> params) {
        return tx.run(cypher, params).list(r -> new NodeRef(r.get("sourceId").asLong(), r.get("neoId").asString()));
    }

    /** 매핑은 (source_table, source_id, node_type) 단위 upsert. 다른 사용자의 행은 갱신하지 않는다. */
    private void upsertMappings(Long userId, String sourceTable, String nodeType, List<NodeRef> refs) {
        if (refs == null || refs.isEmpty()) return;
        Map<Long, String> unique = refs.stream()
                .collect(Collectors.toMap(NodeRef::sourceId, NodeRef::neoId, (a, b) -> b, LinkedHashMap::new));
        List<Object[]> args = unique.entrySet().stream()
                .map(e -> new Object[]{userId, sourceTable, e.getKey(), e.getValue(), nodeType})
                .toList();
        jdbcTemplate.batchUpdate(UPSERT_MAPPING_SQL, args);
    }

    private void deleteMapping(Long userId, String sourceTable, Long sourceId, String nodeType) {
        jdbcTemplate.update(DELETE_MAPPING_SQL, userId, sourceTable, sourceId, nodeType);
    }

    private void withSession(java.util.function.Consumer<Session> work) {
        try (Session session = neo4jDriver.session()) {
            work.accept(session);
        }
    }

    private <T> T withSessionResult(Function<Session, T> work) {
        try (Session session = neo4jDriver.session()) {
            return work.apply(session);
        }
    }

    /**
     * 부가 작업 실행. 활성 트랜잭션이 있으면 커밋 후 새 트랜잭션에서 실행하고, 실패는 로그만 남긴다.
     */
    private void sideEffect(String operation, Long userId, Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 커밋 이후의 DB 접근은 반드시 새 트랜잭션에서 (기존 트랜잭션은 이미 종료됨)
                    runSafely(operation, userId, () -> inNewTransaction(work));
                }
            });
        } else {
            runSafely(operation, userId, work);
        }
    }

    private void runSafely(String operation, Long userId, Runnable work) {
        try {
            work.run();
        } catch (Exception e) {
            log.warn("Graph {} failed for user {}: {}", operation, userId, e.toString());
        }
    }

    /** afterCommit 내 DB 쓰기용: 매핑 upsert/delete를 새 트랜잭션으로 감싼다. */
    private void inNewTransaction(Runnable r) {
        requiresNewTx.executeWithoutResult(status -> r.run());
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        if (list.isEmpty()) return List.of();
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(list.size(), i + size)));
        }
        return parts;
    }

    private String extractCategory(String tag) {
        if (tag.contains("5K")) return "5K";
        if (tag.contains("10K")) return "10K";
        if (tag.contains("하프")) return "하프";
        if (tag.contains("풀")) return "풀";
        return "custom";
    }
}
