package com.pios.repository;

import com.pios.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
    long deleteByExpiresAtBefore(Instant now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int purgeExpired(@Param("now") Instant now);
}
