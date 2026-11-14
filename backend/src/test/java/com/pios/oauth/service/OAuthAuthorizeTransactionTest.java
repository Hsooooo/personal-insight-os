package com.pios.oauth.service;

import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthTokens;
import com.pios.oauth.domain.OAuthAuthorizationRequest;
import com.pios.oauth.repository.OAuthAuthorizationCodeRepository;
import com.pios.oauth.repository.OAuthAuthorizationRequestRepository;
import com.pios.oauth.repository.OAuthGrantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the (potentially slow) client resolution / CIMD fetch in /authorize runs with no transaction
 * (= no pooled JDBC connection held), using a real Spring transactional proxy.
 */
@SpringJUnitConfig(OAuthAuthorizeTransactionTest.Config.class)
class OAuthAuthorizeTransactionTest {

    static final AtomicReference<Boolean> txActiveDuringResolve = new AtomicReference<>();
    static final AtomicReference<Boolean> txActiveDuringDecide = new AtomicReference<>();

    /** Minimal transaction manager: marks a transaction active without any resource. */
    static class NoopTxManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoopTxManager();
        }

        @Bean
        OAuthClientService clientService() {
            OAuthClientService cs = mock(OAuthClientService.class);
            when(cs.resolve(anyString(), any())).thenAnswer(inv -> {
                txActiveDuringResolve.set(TransactionSynchronizationManager.isActualTransactionActive());
                return new ResolvedClient(inv.getArgument(0), "App", List.of("https://app.example/cb"), true);
            });
            return cs;
        }

        @Bean
        OAuthAuthorizationRequestRepository requestRepository() {
            OAuthAuthorizationRequestRepository repo = mock(OAuthAuthorizationRequestRepository.class);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(repo.findByIdForUpdate(anyString())).thenAnswer(inv -> {
                txActiveDuringDecide.set(TransactionSynchronizationManager.isActualTransactionActive());
                return Optional.of(OAuthAuthorizationRequest.builder().id("r").clientId("c")
                        .redirectUri("https://app.example/cb").codeChallenge("x").scope("mcp:read")
                        .resource("https://pios.example/mcp").expiresAt(Instant.now().plusSeconds(60)).build());
            });
            return repo;
        }

        @Bean
        OAuthAuthorizationService authorizationService(OAuthClientService cs, OAuthAuthorizationRequestRepository repo) {
            return new OAuthAuthorizationService(
                    new OAuthProperties("https://pios.example", "https://pios.example/mcp", "https://pios.example"),
                    cs, repo, mock(OAuthAuthorizationCodeRepository.class), mock(OAuthGrantRepository.class));
        }
    }

    @Autowired
    private OAuthAuthorizationService service;

    @Test
    void clientResolutionRunsOutsideTransaction() {
        service.authorize(new OAuthAuthorizationService.AuthorizeParams("code",
                "https://app.example/client.json", "https://app.example/cb",
                OAuthTokens.s256Challenge(OAuthTokens.randomToken(48)), "S256", null, null, null), "1.1.1.1");
        assertThat(txActiveDuringResolve.get()).isFalse();

        // sanity check that the harness does detect transactions (decide() is @Transactional)
        service.decide("r", 1L, false);
        assertThat(txActiveDuringDecide.get()).isTrue();
    }
}
