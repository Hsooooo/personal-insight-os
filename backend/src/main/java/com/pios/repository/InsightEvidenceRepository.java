package com.pios.repository;

import com.pios.domain.InsightEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsightEvidenceRepository extends JpaRepository<InsightEvidence, Long> {
    List<InsightEvidence> findByInsightId(Long insightId);
}
