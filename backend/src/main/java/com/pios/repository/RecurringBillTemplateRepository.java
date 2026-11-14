package com.pios.repository;

import java.util.Optional;
import com.pios.domain.RecurringBillTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecurringBillTemplateRepository extends JpaRepository<RecurringBillTemplate, Long> {
    List<RecurringBillTemplate> findByUserIdOrderByNameAsc(Long userId);
    Optional<RecurringBillTemplate> findByIdAndUserId(Long id, Long userId);
}
