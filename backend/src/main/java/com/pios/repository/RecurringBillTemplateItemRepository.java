package com.pios.repository;

import com.pios.domain.RecurringBillTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecurringBillTemplateItemRepository extends JpaRepository<RecurringBillTemplateItem, Long> {
    List<RecurringBillTemplateItem> findByVersionIdOrderBySortOrderAsc(Long versionId);
}
