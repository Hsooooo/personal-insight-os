package com.pios.oauth.repository;

import com.pios.oauth.domain.OAuthGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OAuthGrantRepository extends JpaRepository<OAuthGrant, Long> {

    List<OAuthGrant> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<OAuthGrant> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndRevokedAtIsNull(Long id);

    /** True if any user has (or had, incl. revoked) authorized this client. */
    boolean existsByClientId(String clientId);

    @Modifying
    @Query("update OAuthGrant g set g.lastUsedAt = :now where g.id = :id")
    int touch(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update OAuthGrant g set g.revokedAt = :now where g.id = :id and g.revokedAt is null")
    int revoke(@Param("id") Long id, @Param("now") Instant now);

    /** Revoked grants older than the cutoff (cascade deletes their codes and refresh tokens). */
    @Modifying
    @Query("delete from OAuthGrant g where g.revokedAt is not null and g.revokedAt < :before")
    int deleteRevokedBefore(@Param("before") Instant before);

    /** Grants without any usable refresh token whose last activity is older than the cutoff. */
    @Modifying
    @Query(value = """
            DELETE FROM oauth_grants g
            WHERE COALESCE(g.last_used_at, g.created_at) < :before
              AND NOT EXISTS (
                SELECT 1 FROM oauth_refresh_tokens r
                WHERE r.grant_id = g.id AND r.used_at IS NULL AND r.revoked_at IS NULL AND r.expires_at > :now)
            """, nativeQuery = true)
    int deleteDeadBefore(@Param("before") Instant before, @Param("now") Instant now);
}
