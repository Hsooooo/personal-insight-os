package com.pios.repository;

import com.pios.domain.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmProviderRepository extends JpaRepository<LlmProvider, Long> {
    List<LlmProvider> findByUserId(Long userId);
    Optional<LlmProvider> findByUserIdAndProviderName(Long userId, String providerName);
}
