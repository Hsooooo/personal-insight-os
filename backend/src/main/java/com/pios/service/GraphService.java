package com.pios.service;

import com.pios.common.AppTimeZones;
import com.pios.dto.GraphDataDto;
import com.pios.dto.GraphNodeDto;
import com.pios.dto.GraphRelationshipDto;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver neo4jDriver;

    public GraphDataDto getGraph(Long userId, int days, String view, String raceCategory) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphRelationshipDto> relationships = new ArrayList<>();

        String cutoffDate = days > 0 ? AppTimeZones.todayKst().minusDays(days).toString() + "T00:00:00" : null;
        boolean includeActivities = "all".equals(view) || "activities".equals(view);
        boolean includeCondition = "all".equals(view) || "condition".equals(view);

        try (Session session = neo4jDriver.session()) {
            // Person node
            String personId = "Person_" + userId;
            nodes.add(GraphNodeDto.builder()
                    .id(personId)
                    .type("Person")
                    .label("Me")
                    .properties(Map.of("userId", userId))
                    .build());

            if (includeActivities) {
                // Activity nodes
                String activityQuery;
                if (cutoffDate != null && raceCategory != null) {
                    activityQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity)-[:TAGGED_AS]->(r:Race) " +
                                    "WHERE a.date >= $cutoffDate AND r.category = $raceCategory RETURN DISTINCT a";
                } else if (cutoffDate != null) {
                    activityQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity) " +
                                    "WHERE a.date >= $cutoffDate RETURN a";
                } else if (raceCategory != null) {
                    activityQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity)-[:TAGGED_AS]->(r:Race) " +
                                    "WHERE r.category = $raceCategory RETURN DISTINCT a";
                } else {
                    activityQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity) RETURN a";
                }
                Map<String, Object> activityParams = new HashMap<>();
                activityParams.put("userId", userId);
                if (cutoffDate != null) activityParams.put("cutoffDate", cutoffDate);
                if (raceCategory != null) activityParams.put("raceCategory", raceCategory);

                var activityResult = session.run(activityQuery, activityParams);
                while (activityResult.hasNext()) {
                    var record = activityResult.next();
                    var node = record.get("a").asNode();
                    String id = String.valueOf(node.id());
                    nodes.add(GraphNodeDto.builder()
                            .id(id)
                            .type("Activity")
                            .label(node.get("name").asString("Activity"))
                            .properties(Map.of("type", node.get("type").asString("")))
                            .build());
                    relationships.add(GraphRelationshipDto.builder()
                            .id("r_" + personId + "_" + id)
                            .type("PERFORMED")
                            .sourceId(personId)
                            .targetId(id)
                            .build());
                }

                // Race nodes
                String raceQuery;
                if (raceCategory != null) {
                    raceQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(:Activity)-[:TAGGED_AS]->(r:Race) " +
                                "WHERE r.category = $raceCategory RETURN DISTINCT r";
                } else {
                    raceQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(:Activity)-[:TAGGED_AS]->(r:Race) " +
                                "RETURN DISTINCT r";
                }
                Map<String, Object> raceParams = new HashMap<>();
                raceParams.put("userId", userId);
                if (raceCategory != null) raceParams.put("raceCategory", raceCategory);

                var raceResult = session.run(raceQuery, raceParams);
                Map<String, String> raceIdMap = new HashMap<>();
                while (raceResult.hasNext()) {
                    var record = raceResult.next();
                    var node = record.get("r").asNode();
                    String id = String.valueOf(node.id());
                    raceIdMap.put(node.get("name").asString(id), id);
                    nodes.add(GraphNodeDto.builder()
                            .id(id)
                            .type("Race")
                            .label(node.get("name").asString("Race"))
                            .properties(Map.of("category", node.get("category").asString("")))
                            .build());
                }

                // TAGGED_AS relationships
                String taggedQuery;
                if (raceCategory != null) {
                    taggedQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity)-[:TAGGED_AS]->(r:Race) " +
                                  "WHERE r.category = $raceCategory RETURN id(a) as actId, id(r) as raceId";
                } else {
                    taggedQuery = "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity)-[:TAGGED_AS]->(r:Race) " +
                                  "RETURN id(a) as actId, id(r) as raceId";
                }
                Map<String, Object> taggedParams = new HashMap<>();
                taggedParams.put("userId", userId);
                if (raceCategory != null) taggedParams.put("raceCategory", raceCategory);

                var taggedResult = session.run(taggedQuery, taggedParams);
                while (taggedResult.hasNext()) {
                    var record = taggedResult.next();
                    String actId = String.valueOf(record.get("actId").asLong());
                    String raceId = String.valueOf(record.get("raceId").asLong());
                    relationships.add(GraphRelationshipDto.builder()
                            .id("r_tagged_" + actId + "_" + raceId)
                            .type("TAGGED_AS")
                            .sourceId(actId)
                            .targetId(raceId)
                            .build());
                }
            }

            if (includeCondition) {
                // Sleep nodes
                String sleepQuery = cutoffDate != null
                    ? "MATCH (p:Person {userId: $userId})-[:HAS_SLEEP]->(s:Sleep) WHERE s.date >= $cutoffDate RETURN s"
                    : "MATCH (p:Person {userId: $userId})-[:HAS_SLEEP]->(s:Sleep) RETURN s";
                Map<String, Object> sleepParams = new HashMap<>();
                sleepParams.put("userId", userId);
                if (cutoffDate != null) sleepParams.put("cutoffDate", cutoffDate);

                var sleepResult = session.run(sleepQuery, sleepParams);
                while (sleepResult.hasNext()) {
                    var record = sleepResult.next();
                    var node = record.get("s").asNode();
                    String id = String.valueOf(node.id());
                    nodes.add(GraphNodeDto.builder()
                            .id(id)
                            .type("Sleep")
                            .label(node.get("date").asString("Sleep"))
                            .build());
                    relationships.add(GraphRelationshipDto.builder()
                            .id("r_" + personId + "_" + id)
                            .type("HAS_SLEEP")
                            .sourceId(personId)
                            .targetId(id)
                            .build());
                }

                // HealthMetric nodes
                String metricQuery = cutoffDate != null
                    ? "MATCH (p:Person {userId: $userId})-[:HAS_METRIC]->(m:HealthMetric) WHERE m.date >= $cutoffDate RETURN m"
                    : "MATCH (p:Person {userId: $userId})-[:HAS_METRIC]->(m:HealthMetric) RETURN m";
                Map<String, Object> metricParams = new HashMap<>();
                metricParams.put("userId", userId);
                if (cutoffDate != null) metricParams.put("cutoffDate", cutoffDate);

                var metricResult = session.run(metricQuery, metricParams);
                while (metricResult.hasNext()) {
                    var record = metricResult.next();
                    var node = record.get("m").asNode();
                    String id = String.valueOf(node.id());
                    nodes.add(GraphNodeDto.builder()
                            .id(id)
                            .type("HealthMetric")
                            .label(node.get("date").asString("Metric"))
                            .build());
                    relationships.add(GraphRelationshipDto.builder()
                            .id("r_" + personId + "_" + id)
                            .type("HAS_METRIC")
                            .sourceId(personId)
                            .targetId(id)
                            .build());
                }
            }

            // Goals, Questions, Insights — always included for "all" view
            if ("all".equals(view) || "insights".equals(view)) {
                appendGoalInsightNodes(session, userId, personId, nodes, relationships);
            }
        }

        return GraphDataDto.builder()
                .nodes(nodes)
                .relationships(relationships)
                .build();
    }

    private void appendGoalInsightNodes(Session session, Long userId, String personId,
                                        List<GraphNodeDto> nodes, List<GraphRelationshipDto> relationships) {
        var goalResult = session.run(
            "MATCH (p:Person {userId: $userId})-[:HAS_GOAL]->(g:Goal) RETURN g",
            Map.of("userId", userId)
        );
        while (goalResult.hasNext()) {
            var node = goalResult.next().get("g").asNode();
            String id = String.valueOf(node.id());
            nodes.add(GraphNodeDto.builder()
                    .id(id)
                    .type("Goal")
                    .label(node.get("title").asString("Goal"))
                    .properties(Map.of(
                            "goalType", node.get("goalType").asString(""),
                            "status", node.get("status").asString("")
                    ))
                    .build());
            relationships.add(GraphRelationshipDto.builder()
                    .id("r_goal_" + personId + "_" + id)
                    .type("HAS_GOAL")
                    .sourceId(personId)
                    .targetId(id)
                    .build());
        }

        var questionResult = session.run(
            "MATCH (p:Person {userId: $userId})-[:ASKED]->(q:Question) RETURN q",
            Map.of("userId", userId)
        );
        while (questionResult.hasNext()) {
            var node = questionResult.next().get("q").asNode();
            String id = String.valueOf(node.id());
            nodes.add(GraphNodeDto.builder()
                    .id(id)
                    .type("Question")
                    .label(node.get("label").asString("Question"))
                    .properties(Map.of("intent", node.get("intent").asString("")))
                    .build());
            relationships.add(GraphRelationshipDto.builder()
                    .id("r_asked_" + personId + "_" + id)
                    .type("ASKED")
                    .sourceId(personId)
                    .targetId(id)
                    .build());
        }

        var insightResult = session.run(
            "MATCH (p:Person {userId: $userId})-[:HAS_INSIGHT]->(i:Insight) RETURN i",
            Map.of("userId", userId)
        );
        Set<String> insightIds = new HashSet<>();
        while (insightResult.hasNext()) {
            var node = insightResult.next().get("i").asNode();
            String id = String.valueOf(node.id());
            insightIds.add(id);
            nodes.add(GraphNodeDto.builder()
                    .id(id)
                    .type("Insight")
                    .label(node.get("label").asString(node.get("title").asString("Insight")))
                    .properties(Map.of(
                            "category", node.get("category").asString(""),
                            "confidence", node.get("confidence").asDouble(0.0)
                    ))
                    .build());
            relationships.add(GraphRelationshipDto.builder()
                    .id("r_insight_" + personId + "_" + id)
                    .type("HAS_INSIGHT")
                    .sourceId(personId)
                    .targetId(id)
                    .build());
        }

        var answeredResult = session.run(
            """
            MATCH (p:Person {userId: $userId})-[:ASKED]->(q:Question)-[:ANSWERED_BY]->(i:Insight)
            RETURN id(q) as qId, id(i) as iId
            """,
            Map.of("userId", userId)
        );
        while (answeredResult.hasNext()) {
            var record = answeredResult.next();
            String qId = String.valueOf(record.get("qId").asLong());
            String iId = String.valueOf(record.get("iId").asLong());
            relationships.add(GraphRelationshipDto.builder()
                    .id("r_answered_" + qId + "_" + iId)
                    .type("ANSWERED_BY")
                    .sourceId(qId)
                    .targetId(iId)
                    .build());
        }

        var derivedResult = session.run(
            """
            MATCH (p:Person {userId: $userId})-[:HAS_INSIGHT]->(i:Insight)-[r:DERIVED_FROM|SUPPORTED_BY]->(n)
            RETURN id(i) as iId, id(n) as nId, type(r) as relType
            """,
            Map.of("userId", userId)
        );
        while (derivedResult.hasNext()) {
            var record = derivedResult.next();
            String iId = String.valueOf(record.get("iId").asLong());
            String nId = String.valueOf(record.get("nId").asLong());
            String relType = record.get("relType").asString();
            if (!insightIds.contains(iId)) {
                continue;
            }
            // Only emit edges if the target node was already included in this graph payload
            boolean targetPresent = nodes.stream().anyMatch(n -> n.getId().equals(nId));
            if (!targetPresent) {
                continue;
            }
            relationships.add(GraphRelationshipDto.builder()
                    .id("r_" + relType.toLowerCase() + "_" + iId + "_" + nId)
                    .type(relType)
                    .sourceId(iId)
                    .targetId(nId)
                    .build());
        }
    }

    public void ensurePersonNode(Long userId) {
        try (Session session = neo4jDriver.session()) {
            session.run(
                "MERGE (p:Person {userId: $userId}) SET p.name = 'User'",
                Map.of("userId", userId)
            );
        }
    }
}
