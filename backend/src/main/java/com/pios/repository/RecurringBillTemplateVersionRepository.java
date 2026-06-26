package com.pios.repository;

import com.pios.domain.RecurringBillTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringBillTemplateVersionRepository extends JpaRepository<RecurringBillTemplateVersion, Long> {
    List<RecurringBillTemplateVersion> findByTemplateIdOrderByVersionDesc(Long templateId);
    Optional<RecurringBillTemplateVersion> findFirstByTemplateIdOrderByVersionDesc(Long templateId);
}
