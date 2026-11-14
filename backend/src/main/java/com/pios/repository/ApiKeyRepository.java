package com.pios.repository;

import com.pios.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByKeyPrefix(String keyPrefix);

    /** Legacy rows created before V23 (no key_prefix). Bounded to keep BCrypt work per request small. */
    List<ApiKey> findTop20ByKeyPrefixIsNullOrderByIdAsc();

    long countByKeyPrefixIsNull();

    void deleteByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :now, k.lastUsedIp = :ip where k.id = :id")
    int touch(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("ip") String ip);
}
