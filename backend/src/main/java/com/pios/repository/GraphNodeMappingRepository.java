package com.pios.repository;

import com.pios.domain.GraphNodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GraphNodeMappingRepository extends JpaRepository<GraphNodeMapping, Long> {
    Optional<GraphNodeMapping> findBySourceTableAndSourceIdAndNodeType(String sourceTable, Long sourceId, String nodeType);
}
