package com.pios.service;

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

    public GraphDataDto getGraph(Long userId) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphRelationshipDto> relationships = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            // Person node
            String personId = "Person_" + userId;
            nodes.add(GraphNodeDto.builder()
                    .id(personId)
                    .type("Person")
                    .label("Me")
                    .properties(Map.of("userId", userId))
                    .build());

            // Activity nodes
            var activityResult = session.run(
                "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity) RETURN a LIMIT 20",
                Map.of("userId", userId)
            );
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

            // Race nodes (via Activity-TAGGED_AS-Race)
            var raceResult = session.run(
                "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(:Activity)-[:TAGGED_AS]->(r:Race) RETURN DISTINCT r LIMIT 20",
                Map.of("userId", userId)
            );
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

            // TAGGED_AS relationships (Activity -> Race)
            var taggedResult = session.run(
                "MATCH (p:Person {userId: $userId})-[:PERFORMED]->(a:Activity)-[:TAGGED_AS]->(r:Race) RETURN id(a) as actId, id(r) as raceId",
                Map.of("userId", userId)
            );
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

            // Sleep nodes
            var sleepResult = session.run(
                "MATCH (p:Person {userId: $userId})-[:HAS_SLEEP]->(s:Sleep) RETURN s LIMIT 14",
                Map.of("userId", userId)
            );
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
            var metricResult = session.run(
                "MATCH (p:Person {userId: $userId})-[:HAS_METRIC]->(m:HealthMetric) RETURN m LIMIT 14",
                Map.of("userId", userId)
            );
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
