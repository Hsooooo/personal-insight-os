package com.pios.repository;

import com.pios.domain.FinanceAccountAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinanceAccountAliasRepository extends JpaRepository<FinanceAccountAlias, Long> {
    List<FinanceAccountAlias> findByAccountIdOrderByAliasNameAsc(Long accountId);
    List<FinanceAccountAlias> findByAccountIdIn(Collection<Long> accountIds);
    Optional<FinanceAccountAlias> findByAccountUserIdAndAliasName(Long userId, String aliasName);
    boolean existsByAccountUserIdAndAliasNameAndAccountIdNot(Long userId, String aliasName, Long accountId);
    void deleteByAccountId(Long accountId);
}
