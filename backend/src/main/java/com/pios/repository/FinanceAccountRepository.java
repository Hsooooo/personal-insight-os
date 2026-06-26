package com.pios.repository;

import com.pios.domain.FinanceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinanceAccountRepository extends JpaRepository<FinanceAccount, Long> {
    List<FinanceAccount> findByUserIdOrderByNameAsc(Long userId);
    Optional<FinanceAccount> findByUserIdAndName(Long userId, String name);
}
