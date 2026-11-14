package com.pios.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginAttemptService service = new LoginAttemptService(
            new SlidingWindowRateLimiter(5, 900_000, now::get),
            new SlidingWindowRateLimiter(30, 900_000, now::get));

    @Test
    void blocksAfterFiveFailuresForSameEmailAndIp() {
        for (int i = 0; i < 5; i++) {
            service.checkAllowed("Me@Example.com", "1.2.3.4");
            service.onFailure("me@example.com", "1.2.3.4");
        }
        assertThatThrownBy(() -> service.checkAllowed("me@example.com", "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);
        // other IP is not affected
        assertThatCode(() -> service.checkAllowed("me@example.com", "5.6.7.8")).doesNotThrowAnyException();
    }

    @Test
    void windowExpiresAfterFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            service.onFailure("me@example.com", "1.2.3.4");
        }
        now.addAndGet(900_001);
        assertThatCode(() -> service.checkAllowed("me@example.com", "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < 4; i++) {
            service.onFailure("me@example.com", "1.2.3.4");
        }
        service.onSuccess("me@example.com", "1.2.3.4");
        service.onFailure("me@example.com", "1.2.3.4");
        assertThatCode(() -> service.checkAllowed("me@example.com", "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void perIpCapAppliesAcrossEmails() {
        for (int i = 0; i < 30; i++) {
            service.onFailure("user" + i + "@example.com", "9.9.9.9");
        }
        assertThatThrownBy(() -> service.checkAllowed("fresh@example.com", "9.9.9.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
