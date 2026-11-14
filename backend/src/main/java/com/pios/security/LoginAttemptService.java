package com.pios.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Brute-force protection for password login: N failures per (email, IP) and a looser
 * per-IP cap within a sliding window → 429.
 */
@Service
public class LoginAttemptService {

    private final SlidingWindowRateLimiter perEmailIp;
    private final SlidingWindowRateLimiter perIp;

    @Autowired
    public LoginAttemptService(@Value("${pios.login-throttle.max-failures:5}") int maxFailures,
                               @Value("${pios.login-throttle.max-failures-per-ip:30}") int maxFailuresPerIp,
                               @Value("${pios.login-throttle.window-seconds:900}") long windowSeconds) {
        this.perEmailIp = new SlidingWindowRateLimiter(maxFailures, windowSeconds * 1000L);
        this.perIp = new SlidingWindowRateLimiter(maxFailuresPerIp, windowSeconds * 1000L);
    }

    LoginAttemptService(SlidingWindowRateLimiter perEmailIp, SlidingWindowRateLimiter perIp) {
        this.perEmailIp = perEmailIp;
        this.perIp = perIp;
    }

    /** @throws TooManyRequestsException when the caller is currently blocked */
    public void checkAllowed(String email, String ip) {
        String key = key(email, ip);
        if (perEmailIp.isLimited(key)) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.",
                    perEmailIp.retryAfterSeconds(key));
        }
        if (perIp.isLimited(ip)) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.",
                    perIp.retryAfterSeconds(ip));
        }
    }

    public void onFailure(String email, String ip) {
        perEmailIp.record(key(email, ip));
        perIp.record(ip);
    }

    public void onSuccess(String email, String ip) {
        perEmailIp.reset(key(email, ip));
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void prune() {
        perEmailIp.prune();
        perIp.prune();
    }

    private static String key(String email, String ip) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + ip;
    }
}
