package com.pios.domain;

import com.pios.dto.EvidenceData;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "insight_evidences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InsightEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insight_id", nullable = false)
    private Insight insight;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(name = "source_table")
    private String sourceTable;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "evidence_summary", columnDefinition = "text")
    private String evidenceSummary;

    private BigDecimal weight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_data", columnDefinition = "jsonb")
    private EvidenceData evidenceData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
