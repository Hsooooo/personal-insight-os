package com.pios.oauth.repository;

import com.pios.oauth.domain.OAuthAuthorizationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthAuthorizationRequestRepository extends JpaRepository<OAuthAuthorizationRequest, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OAuthAuthorizationRequest r where r.id = :id")
    Optional<OAuthAuthorizationRequest> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("delete from OAuthAuthorizationRequest r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
