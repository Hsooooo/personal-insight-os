package com.pios.oauth.repository;

import com.pios.oauth.domain.OAuthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthClientRepository extends JpaRepository<OAuthClient, Long> {
    Optional<OAuthClient> findByClientId(String clientId);

    /** DCR clients registered before the cutoff with no grant, pending request or code referencing them. */
    @Modifying
    @Query(value = """
            DELETE FROM oauth_clients c
            WHERE c.created_at < :before
              AND NOT EXISTS (SELECT 1 FROM oauth_grants g WHERE g.client_id = c.client_id)
              AND NOT EXISTS (SELECT 1 FROM oauth_authorization_requests r WHERE r.client_id = c.client_id)
              AND NOT EXISTS (SELECT 1 FROM oauth_authorization_codes a WHERE a.client_id = c.client_id)
            """, nativeQuery = true)
    int deleteUnusedBefore(@Param("before") Instant before);
}
