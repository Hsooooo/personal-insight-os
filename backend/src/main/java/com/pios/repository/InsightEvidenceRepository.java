package com.pios.repository;

import com.pios.domain.InsightEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InsightEvidenceRepository extends JpaRepository<InsightEvidence, Long> {
    List<InsightEvidence> findByInsightId(Long insightId);

    @Query("SELECT e FROM InsightEvidence e WHERE e.insight.id IN :insightIds ORDER BY e.id")
    List<InsightEvidence> findByInsightIdIn(@Param("insightIds") Collection<Long> insightIds);

    void deleteByInsightId(Long insightId);
}
