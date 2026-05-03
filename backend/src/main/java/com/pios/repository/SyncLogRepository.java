package com.pios.repository;

import com.pios.domain.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findByUserIdOrderByStartedAtDesc(Long userId);

    @Query("SELECT s FROM SyncLog s WHERE s.user.id = :userId AND s.providerType = :providerType ORDER BY s.startedAt DESC")
    List<SyncLog> findByUserIdAndProviderTypeOrderByStartedAtDesc(
            @Param("userId") Long userId,
            @Param("providerType") String providerType);

    @Query("SELECT s FROM SyncLog s WHERE s.user.id = :userId AND s.providerType = :providerType ORDER BY s.startedAt DESC LIMIT 1")
    Optional<SyncLog> findLatestByUserIdAndProviderType(
            @Param("userId") Long userId,
            @Param("providerType") String providerType);
}
