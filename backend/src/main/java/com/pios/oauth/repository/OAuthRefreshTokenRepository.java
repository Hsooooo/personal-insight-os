package com.pios.oauth.repository;

import com.pios.oauth.domain.OAuthRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthRefreshTokenRepository extends JpaRepository<OAuthRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OAuthRefreshToken t where t.tokenHash = :hash")
    Optional<OAuthRefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    Optional<OAuthRefreshToken> findByTokenHash(String hash);

    @Modifying
    @Query("update OAuthRefreshToken t set t.revokedAt = :now where t.grantId = :grantId and t.revokedAt is null")
    int revokeByGrantId(@Param("grantId") Long grantId, @Param("now") Instant now);

    @Modifying
    @Query("delete from OAuthRefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
