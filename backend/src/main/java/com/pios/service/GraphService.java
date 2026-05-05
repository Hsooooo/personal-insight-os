package com.pios.service;

import com.pios.dto.GraphDataDto;
import com.pios.dto.GraphNodeDto;
import com.pios.dto.GraphRelationshipDto;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver neo4jDriver;

    public GraphDataDto getGraph(Long userId, int days, String view, String raceCategory) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphRelationshipDto> relationships = new ArrayList<>();

        String cutoffDate = days > 0 ? LocalDate.now().minusDays(days).toString() + "T00:00:00" : null;
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
        }

        return GraphDataDto.builder()
                .nodes(nodes)
                .relationships(relationships)
                .build();
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
