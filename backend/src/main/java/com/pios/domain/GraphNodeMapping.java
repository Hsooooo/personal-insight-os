package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "graph_node_mappings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_table", "source_id", "node_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GraphNodeMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_table", nullable = false)
    private String sourceTable;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "neo4j_node_id", nullable = false)
    private String neo4jNodeId;

    @Column(name = "node_type", nullable = false)
    private String nodeType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
