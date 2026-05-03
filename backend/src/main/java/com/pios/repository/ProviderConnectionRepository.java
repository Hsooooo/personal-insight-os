package com.pios.repository;

import com.pios.domain.ProviderConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderConnectionRepository extends JpaRepository<ProviderConnection, Long> {
    List<ProviderConnection> findByUserId(Long userId);
    Optional<ProviderConnection> findByUserIdAndProviderType(Long userId, String providerType);
}
