package com.pios.oauth.service;

import com.pios.oauth.repository.OAuthAuthorizationCodeRepository;
import com.pios.oauth.repository.OAuthClientRepository;
import com.pios.oauth.repository.OAuthAuthorizationRequestRepository;
import com.pios.oauth.repository.OAuthGrantRepository;
import com.pios.oauth.repository.OAuthRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Hourly purge of expired OAuth requests, codes, refresh tokens, dead/revoked grants and unused DCR clients. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthCleanupService {

    private final OAuthAuthorizationRequestRepository requestRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final OAuthRefreshTokenRepository refreshTokenRepository;
    private final OAuthGrantRepository grantRepository;
    private final OAuthClientRepository clientRepository;
    private final OAuthTokenService tokenService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        int requests = requestRepository.deleteExpired(now);
        // keep used codes a day past expiry so late replays still trigger grant revocation
        int codes = codeRepository.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
        int tokens = refreshTokenRepository.deleteExpired(now);
        int revoked = grantRepository.deleteRevokedBefore(now.minus(Duration.ofDays(7)));
        int dead = grantRepository.deleteDeadBefore(now.minus(Duration.ofDays(1)), now);
        // DCR clients nobody ever authorized (CIMD clients are not stored in oauth_clients, only cached in memory)
        int clients = clientRepository.deleteUnusedBefore(now.minus(Duration.ofDays(30)));
        tokenService.trimTouchCache();
        if (requests + codes + tokens + revoked + dead + clients > 0) {
            log.info("OAuth cleanup: requests={}, codes={}, refreshTokens={}, revokedGrants={}, deadGrants={}, clients={}",
                    requests, codes, tokens, revoked, dead, clients);
        }
    }
}
