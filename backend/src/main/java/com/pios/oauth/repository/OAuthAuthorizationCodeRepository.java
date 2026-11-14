package com.pios.oauth.repository;

import com.pios.oauth.domain.OAuthAuthorizationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OAuthAuthorizationCode c where c.codeHash = :hash")
    Optional<OAuthAuthorizationCode> findByCodeHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("delete from OAuthAuthorizationCode c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
