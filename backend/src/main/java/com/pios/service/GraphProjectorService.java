package com.pios.service;

import com.pios.common.AppTimeZones;
import com.pios.domain.*;
import com.pios.repository.*;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GraphProjectorService {

    private final Driver neo4jDriver;
    private final ActivityRepository activityRepo;
    private final GarminDailyHealthMetricRepository healthRepo;
    private final GarminSleepSessionRepository sleepRepo;
    private final GraphNodeMappingRepository mappingRepo;
    private final GoalRepository goalRepo;
    private final QuestionRepository questionRepo;
    private final InsightRepository insightRepo;
    private final InsightEvidenceRepository evidenceRepo;

    @Transactional
    public void projectUserData(Long userId) {
        try (Session session = neo4jDriver.session()) {
            // Create Person node
            session.run(
                "MERGE (p:Person {userId: $userId}) SET p.name = 'User'",
                Map.of("userId", userId)
            );

            // Project activities
            activityRepo.findByUserIdOrderByStartTimeDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                    .getContent().forEach(a -> {
                        projectActivityInternal(session, userId, a);
                    });

            // Project health metrics
            LocalDate today = AppTimeZones.todayKst();
            healthRepo.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
                    userId, today.minusDays(30), today)
                    .forEach(h -> {
                        var result = session.run(
                            """
                            MERGE (n:HealthMetric {sourceId: $sourceId, userId: $userId})
                            SET n.date = $date, n.rhr = $rhr, n.stress = $stress, n.weight = $weight
                            WITH n
                            MATCH (p:Person {userId: $userId})
                            MERGE (p)-[:HAS_METRIC]->(n)
                            RETURN id(n) as neoId
                            """,
                            Map.of(
                                "sourceId", h.getId(),
                                "userId", userId,
                                "date", h.getMetricDate().toString(),
                                "rhr", h.getRestingHeartRate() != null ? h.getRestingHeartRate() : 0,
                                "stress", h.getStressAvg() != null ? h.getStressAvg().doubleValue() : 0.0,
                                "weight", h.getWeightKg() != null ? h.getWeightKg().doubleValue() : 0.0
                            )
                        );
                        if (result.hasNext()) {
                            long neoId = result.next().get("neoId").asLong();
                            saveMapping(userId, "garmin_daily_health_metrics", h.getId(), String.valueOf(neoId), "HealthMetric");
                        }
                    });

            // Project sleep
            sleepRepo.findByUserIdAndSleepDateBetweenOrderBySleepDateDesc(
                    userId, today.minusDays(30), today)
                    .forEach(s -> {
                        var result = session.run(
                            """
                            MERGE (n:Sleep {sourceId: $sourceId, userId: $userId})
                            SET n.date = $date, n.score = $score, n.duration = $duration
                            WITH n
                            MATCH (p:Person {userId: $userId})
                            MERGE (p)-[:HAS_SLEEP]->(n)
                            RETURN id(n) as neoId
                            """,
                            Map.of(
                                "sourceId", s.getId(),
                                "userId", userId,
                                "date", s.getSleepDate().toString(),
                                "score", s.getSleepScore() != null ? s.getSleepScore() : 0,
                                "duration", s.getTotalSleepSeconds() != null ? s.getTotalSleepSeconds() : 0
                            )
                        );
                        if (result.hasNext()) {
                            long neoId = result.next().get("neoId").asLong();
                            saveMapping(userId, "garmin_sleep_sessions", s.getId(), String.valueOf(neoId), "Sleep");
                        }
                    });

            goalRepo.findByUserId(userId).forEach(g -> projectGoalInternal(session, userId, g));
            questionRepo.findByUserIdOrderByCreatedAtDesc(userId)
                    .forEach(q -> projectQuestionInternal(session, userId, q));
            insightRepo.findByUserIdOrderByCreatedAtDesc(userId)
                    .forEach(i -> projectInsightInternal(session, userId, i));
        }
    }

    public void projectGoal(Long userId, Goal goal) {
        try (Session session = neo4jDriver.session()) {
            ensurePerson(session, userId);
            projectGoalInternal(session, userId, goal);
        }
    }

    public void deleteGoal(Long userId, Long goalId) {
        try (Session session = neo4jDriver.session()) {
            session.run(
                "MATCH (g:Goal {sourceId: $sourceId, userId: $userId}) DETACH DELETE g",
                Map.of("sourceId", goalId, "userId", userId)
            );
        }
        mappingRepo.findBySourceTableAndSourceIdAndNodeType("goals", goalId, "Goal")
                .ifPresent(mappingRepo::delete);
    }

    public void projectQuestion(Long userId, Question question) {
        try (Session session = neo4jDriver.session()) {
            ensurePerson(session, userId);
            projectQuestionInternal(session, userId, question);
        }
    }

    public void projectInsight(Long userId, Insight insight) {
        try (Session session = neo4jDriver.session()) {
            ensurePerson(session, userId);
            projectInsightInternal(session, userId, insight);
        }
    }

    public void deleteInsight(Long userId, Long insightId) {
        try (Session session = neo4jDriver.session()) {
            session.run(
                "MATCH (i:Insight {sourceId: $sourceId, userId: $userId}) DETACH DELETE i",
                Map.of("sourceId", insightId, "userId", userId)
            );
        }
        mappingRepo.findBySourceTableAndSourceIdAndNodeType("insights", insightId, "Insight")
                .ifPresent(mappingRepo::delete);
    }

    public void projectActivity(Long userId, Activity a) {
        try (Session session = neo4jDriver.session()) {
            projectActivityInternal(session, userId, a);
        }
    }

    public void deleteActivity(Long userId, Long activityId) {
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // Delete TAGGED_AS relationship and Race node if no longer referenced
                tx.run(
                    """
                    MATCH (a:Activity {sourceId: $sourceId, userId: $userId})
                    OPTIONAL MATCH (a)-[t:TAGGED_AS]->(r:Race)
                    DELETE t
                    WITH r
                    WHERE r IS NOT NULL
                    OPTIONAL MATCH (r)<-[:TAGGED_AS]-(other:Activity)
                    WITH r, other
                    WHERE other IS NULL
                    DELETE r
                    """,
                    Map.of("sourceId", activityId, "userId", userId)
                );

                // Delete PERFORMED relationship and Activity node
                tx.run(
                    "MATCH (a:Activity {sourceId: $sourceId, userId: $userId}) DETACH DELETE a",
                    Map.of("sourceId", activityId, "userId", userId)
                );

                // Delete mapping
                tx.run(
                    "MATCH (m:GraphNodeMapping {sourceTable: 'activities', sourceId: $sourceId, nodeType: 'Activity'}) DELETE m",
                    Map.of("sourceId", activityId)
                );
                return null;
            });

            // Also delete from PostgreSQL mapping table
            mappingRepo.findBySourceTableAndSourceIdAndNodeType("activities", activityId, "Activity")
                    .ifPresent(mappingRepo::delete);
        }
    }

    private void projectActivityInternal(Session session, Long userId, Activity a) {
        String userTag = a.getUserTag() != null ? a.getUserTag() : "";
        Double distance = a.getDistanceMeters() != null ? a.getDistanceMeters().doubleValue() : null;
        String sourceType = a.getSourceType() != null ? a.getSourceType() : "GARMIN";

        var result = session.run(
            """
            MERGE (n:Activity {sourceId: $sourceId, userId: $userId})
            SET n.name = $name, n.type = $type, n.date = $date,
                n.distance = $distance, n.userTag = $userTag, n.sourceType = $sourceType
            WITH n
            MATCH (p:Person {userId: $userId})
            MERGE (p)-[:PERFORMED]->(n)
            RETURN id(n) as neoId
            """,
            Map.of(
                "sourceId", a.getId(),
                "userId", userId,
                "name", a.getActivityName() != null ? a.getActivityName() : "Activity",
                "type", a.getActivityType() != null ? a.getActivityType() : "UNKNOWN",
                "date", a.getStartTime() != null ? a.getStartTime().toString() : "",
                "distance", distance != null ? distance : 0.0,
                "userTag", userTag,
                "sourceType", sourceType
            )
        );
        if (result.hasNext()) {
            long neoId = result.next().get("neoId").asLong();
            saveMapping(userId, "activities", a.getId(), String.valueOf(neoId), "Activity");
        }

        // Create Race node and TAGGED_AS relationship
        if (a.getUserTag() != null && !a.getUserTag().isBlank()) {
            projectRaceNode(session, userId, a.getId(), a.getUserTag());
        }
    }

    public void updateActivityTag(Long userId, Long activityId, String newTag) {
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 1. Update Activity userTag property
                tx.run(
                    "MATCH (a:Activity {sourceId: $sourceId, userId: $userId}) SET a.userTag = $newTag",
                    Map.of("sourceId", activityId, "userId", userId,
                           "newTag", newTag != null ? newTag : "")
                );

                // 2. Remove old TAGGED_AS relationship and orphan Race nodes
                tx.run(
                    """
                    MATCH (a:Activity {sourceId: $sourceId, userId: $userId})
                    OPTIONAL MATCH (a)-[t:TAGGED_AS]->(r:Race)
                    DELETE t
                    WITH r
                    WHERE r IS NOT NULL
                    OPTIONAL MATCH (r)<-[:TAGGED_AS]-(other:Activity)
                    WITH r, other
                    WHERE other IS NULL
                    DELETE r
                    """,
                    Map.of("sourceId", activityId, "userId", userId)
                );

                // 3. Create new Race node and relationship if tag exists
                if (newTag != null && !newTag.isBlank()) {
                    String category = extractCategory(newTag);
                    var result = tx.run(
                        """
                        MATCH (a:Activity {sourceId: $sourceId, userId: $userId})
                        MERGE (r:Race {userId: $userId, name: $tagName})
                        SET r.category = $category
                        MERGE (a)-[:TAGGED_AS]->(r)
                        RETURN id(r) as neoId
                        """,
                        Map.of("sourceId", activityId, "userId", userId,
                               "tagName", newTag, "category", category)
                    );
                    if (result.hasNext()) {
                        long neoId = result.next().get("neoId").asLong();
                        saveMapping(userId, "activities_race", activityId, String.valueOf(neoId), "Race");
                    }
                }
                return null;
            });
        }
    }

    private void projectRaceNode(Session session, Long userId, Long activityId, String tag) {
        String category = extractCategory(tag);
        var result = session.run(
            """
            MATCH (a:Activity {sourceId: $sourceId, userId: $userId})
            MERGE (r:Race {userId: $userId, name: $tagName})
            SET r.category = $category
            MERGE (a)-[:TAGGED_AS]->(r)
            RETURN id(r) as neoId
            """,
            Map.of("sourceId", activityId, "userId", userId,
                   "tagName", tag, "category", category)
        );
        if (result.hasNext()) {
            long neoId = result.next().get("neoId").asLong();
            saveMapping(userId, "activities_race", activityId, String.valueOf(neoId), "Race");
        }
    }

    private String extractCategory(String tag) {
        if (tag.contains("5K")) return "5K";
        if (tag.contains("10K")) return "10K";
        if (tag.contains("하프")) return "하프";
        if (tag.contains("풀")) return "풀";
        return "custom";
    }

    private void ensurePerson(Session session, Long userId) {
        session.run(
            "MERGE (p:Person {userId: $userId}) SET p.name = 'User'",
            Map.of("userId", userId)
        );
    }

    private void projectGoalInternal(Session session, Long userId, Goal goal) {
        var result = session.run(
            """
            MERGE (g:Goal {sourceId: $sourceId, userId: $userId})
            SET g.title = $title, g.goalType = $goalType, g.status = $status,
                g.targetValue = $targetValue, g.targetUnit = $targetUnit
            WITH g
            MATCH (p:Person {userId: $userId})
            MERGE (p)-[:HAS_GOAL]->(g)
            RETURN id(g) as neoId
            """,
            Map.of(
                "sourceId", goal.getId(),
                "userId", userId,
                "title", goal.getTitle() != null ? goal.getTitle() : "Goal",
                "goalType", goal.getGoalType() != null ? goal.getGoalType() : "",
                "status", goal.getStatus() != null ? goal.getStatus() : "ACTIVE",
                "targetValue", goal.getTargetValue() != null ? goal.getTargetValue().doubleValue() : 0.0,
                "targetUnit", goal.getTargetUnit() != null ? goal.getTargetUnit() : ""
            )
        );
        if (result.hasNext()) {
            long neoId = result.next().get("neoId").asLong();
            saveMapping(userId, "goals", goal.getId(), String.valueOf(neoId), "Goal");
        }
    }

    private void projectQuestionInternal(Session session, Long userId, Question question) {
        String text = question.getQuestionText() != null ? question.getQuestionText() : "";
        String label = text.length() > 40 ? text.substring(0, 40) + "…" : text;
        var result = session.run(
            """
            MERGE (q:Question {sourceId: $sourceId, userId: $userId})
            SET q.text = $text, q.intent = $intent, q.label = $label
            WITH q
            MATCH (p:Person {userId: $userId})
            MERGE (p)-[:ASKED]->(q)
            RETURN id(q) as neoId
            """,
            Map.of(
                "sourceId", question.getId(),
                "userId", userId,
                "text", text,
                "intent", question.getIntent() != null ? question.getIntent() : "",
                "label", label.isBlank() ? "Question" : label
            )
        );
        if (result.hasNext()) {
            long neoId = result.next().get("neoId").asLong();
            saveMapping(userId, "questions", question.getId(), String.valueOf(neoId), "Question");
        }
    }

    private void projectInsightInternal(Session session, Long userId, Insight insight) {
        String title = insight.getTitle() != null ? insight.getTitle() : "Insight";
        String label = title.length() > 40 ? title.substring(0, 40) + "…" : title;
        var result = session.run(
            """
            MERGE (i:Insight {sourceId: $sourceId, userId: $userId})
            SET i.title = $title, i.category = $category, i.confidence = $confidence,
                i.isSaved = $isSaved, i.label = $label
            WITH i
            MATCH (p:Person {userId: $userId})
            MERGE (p)-[:HAS_INSIGHT]->(i)
            RETURN id(i) as neoId
            """,
            Map.of(
                "sourceId", insight.getId(),
                "userId", userId,
                "title", title,
                "category", insight.getCategory() != null ? insight.getCategory() : "",
                "confidence", insight.getConfidence() != null ? insight.getConfidence().doubleValue() : 0.0,
                "isSaved", Boolean.TRUE.equals(insight.getIsSaved()),
                "label", label
            )
        );
        if (result.hasNext()) {
            long neoId = result.next().get("neoId").asLong();
            saveMapping(userId, "insights", insight.getId(), String.valueOf(neoId), "Insight");
        }

        if (insight.getQuestion() != null && insight.getQuestion().getId() != null) {
            session.run(
                """
                MATCH (q:Question {sourceId: $questionId, userId: $userId})
                MATCH (i:Insight {sourceId: $insightId, userId: $userId})
                MERGE (q)-[:ANSWERED_BY]->(i)
                """,
                Map.of(
                    "questionId", insight.getQuestion().getId(),
                    "insightId", insight.getId(),
                    "userId", userId
                )
            );
        }

        for (InsightEvidence evidence : evidenceRepo.findByInsightId(insight.getId())) {
            linkInsightToEvidenceSource(session, userId, insight.getId(), evidence);
        }
    }

    private void linkInsightToEvidenceSource(Session session, Long userId, Long insightId, InsightEvidence evidence) {
        if (evidence.getSourceTable() == null || evidence.getSourceId() == null) {
            return;
        }
        String nodeLabel = switch (evidence.getSourceTable()) {
            case "activities", "garmin_activities" -> "Activity";
            case "garmin_sleep_sessions" -> "Sleep";
            case "garmin_daily_health_metrics" -> "HealthMetric";
            default -> null;
        };
        if (nodeLabel == null) {
            return;
        }
        session.run(
            """
            MATCH (i:Insight {sourceId: $insightId, userId: $userId})
            MATCH (n:%s {sourceId: $sourceId, userId: $userId})
            MERGE (i)-[:DERIVED_FROM]->(n)
            MERGE (i)-[:SUPPORTED_BY]->(n)
            """.formatted(nodeLabel),
            Map.of(
                "insightId", insightId,
                "userId", userId,
                "sourceId", evidence.getSourceId()
            )
        );
    }

    private void saveMapping(Long userId, String table, Long sourceId, String neoId, String nodeType) {
        if (mappingRepo.findBySourceTableAndSourceIdAndNodeType(table, sourceId, nodeType).isEmpty()) {
            mappingRepo.save(GraphNodeMapping.builder()
                    .user(User.builder().id(userId).build())
                    .sourceTable(table)
                    .sourceId(sourceId)
                    .neo4jNodeId(neoId)
                    .nodeType(nodeType)
                    .build());
        }
    }
}
