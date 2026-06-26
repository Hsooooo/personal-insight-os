package com.pios.repository;

import com.pios.domain.FinanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {
    List<FinanceTransaction> findByUserIdOrderByTransactionAtAscIdAsc(Long userId);
    List<FinanceTransaction> findByUserIdAndCycleIdOrderByTransactionAtAscIdAsc(Long userId, Long cycleId);
    Optional<FinanceTransaction> findByIdAndUserId(Long id, Long userId);
    Optional<FinanceTransaction> findByUserIdAndSourceFingerprint(Long userId, String sourceFingerprint);
    List<FinanceTransaction> findByUserIdAndSourceFingerprintIn(Long userId, Collection<String> fingerprints);
    List<FinanceTransaction> findByUserIdAndTransactionDateAndAmountAndFlowType(
            Long userId, LocalDate transactionDate, BigDecimal amount, String flowType);
    List<FinanceTransaction> findByUserIdAndAssetIn(Long userId, Collection<String> assets);
    List<FinanceTransaction> findByUserIdAndAccountIdIsNullOrderByTransactionAtAscIdAsc(Long userId);
}
