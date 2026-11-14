package com.pios.service;

import com.pios.domain.ApiKey;
import com.pios.dto.ApiKeyResponse;
import com.pios.repository.ApiKeyRepository;
import com.pios.security.SlidingWindowRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    public static final String KEY_PREFIX = "pios_";

    private static final int KEY_BYTES = 32;
    private static final int LOOKUP_PREFIX_LEN = 16;
    private static final int MAX_KEY_LENGTH = 128;
    /** Successful verifications are cached briefly so BCrypt doesn't run on every MCP request. */
    private static final long VERIFIED_CACHE_MILLIS = 60_000;
    /** Unknown keys are cached negatively to avoid repeated BCrypt work for the same bad key. */
    private static final long NEGATIVE_CACHE_MILLIS = 300_000;
    private static final long TOUCH_INTERVAL_MILLIS = 60_000;
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    private final Map<String, CachedKey> verifiedCache = new ConcurrentHashMap<>();
    private final Map<String, Long> negativeCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastTouch = new ConcurrentHashMap<>();
    LongSupplier clock = System::currentTimeMillis;
    /** Legacy (un-prefixed) BCrypt scans per client IP per minute. */
    private final SlidingWindowRateLimiter legacyLimiter = new SlidingWindowRateLimiter(20, 60_000L);
    /** Once no legacy rows exist they never come back (new keys always get a prefix). */
    private volatile boolean noLegacyKeys = false;

    /** Authenticated API key: owner + key id. */
    public record ApiKeyPrincipal(Long keyId, Long userId) {
    }

    private record CachedKey(ApiKeyPrincipal principal, long expiresAt) {
    }

    public static boolean looksLikeApiKey(String value) {
        return value != null && value.startsWith(KEY_PREFIX);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listKeys(Long userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiKeyResponse createKey(Long userId, String name) {
        String rawKey = generateRawKey();
        String keyHash = passwordEncoder.encode(rawKey);

        ApiKey apiKey = ApiKey.builder()
                .userId(userId)
                .name(name)
                .keyHash(keyHash)
                .keyPrefix(lookupPrefix(rawKey))
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        ApiKeyResponse response = toResponse(saved);
        response.setKey(rawKey); // 생성 시에만 원본 키 노출
        return response;
    }

    @Transactional
    public void deleteKey(Long userId, Long keyId) {
        apiKeyRepository.deleteByIdAndUserId(keyId, userId);
        verifiedCache.values().removeIf(c -> c.principal().keyId().equals(keyId));
        lastTouch.remove(keyId);
    }

    /**
     * Verifies a raw {@code pios_} key. Updates last_used_at at most once per minute per key.
     */
    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String rawKey, String clientIp) {
        if (!looksLikeApiKey(rawKey) || rawKey.length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        long now = clock.getAsLong();
        String cacheKey = sha256(rawKey);

        CachedKey cached = verifiedCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) {
            touch(cached.principal().keyId(), clientIp, now);
            return Optional.of(cached.principal());
        }
        Long negativeUntil = negativeCache.get(cacheKey);
        if (negativeUntil != null && negativeUntil > now) {
            return Optional.empty();
        }

        Optional<ApiKeyPrincipal> verified = verify(rawKey, clientIp);
        trimCaches(now);
        if (verified.isPresent()) {
            negativeCache.remove(cacheKey);
            verifiedCache.put(cacheKey, new CachedKey(verified.get(), now + VERIFIED_CACHE_MILLIS));
            touch(verified.get().keyId(), clientIp, now);
        } else {
            negativeCache.put(cacheKey, now + NEGATIVE_CACHE_MILLIS);
        }
        return verified;
    }

    private Optional<ApiKeyPrincipal> verify(String rawKey, String clientIp) {
        String prefix = lookupPrefix(rawKey);
        for (ApiKey apiKey : apiKeyRepository.findByKeyPrefix(prefix)) {
            if (passwordEncoder.matches(rawKey, apiKey.getKeyHash())) {
                return Optional.of(new ApiKeyPrincipal(apiKey.getId(), apiKey.getUserId()));
            }
        }
        return verifyLegacy(rawKey, prefix, clientIp);
    }

    /**
     * Legacy keys created before key_prefix existed (V23): bounded BCrypt scan, rate-limited per client IP,
     * skipped entirely once no legacy rows remain (none are ever created again). Migrated on first use.
     */
    private Optional<ApiKeyPrincipal> verifyLegacy(String rawKey, String prefix, String clientIp) {
        if (noLegacyKeys) {
            return Optional.empty();
        }
        if (apiKeyRepository.countByKeyPrefixIsNull() == 0) {
            noLegacyKeys = true;
            return Optional.empty();
        }
        if (!legacyLimiter.tryAcquire(clientIp == null ? "unknown" : clientIp)) {
            log.warn("Legacy API key lookup rate-limited for {}", clientIp);
            return Optional.empty();
        }
        for (ApiKey apiKey : apiKeyRepository.findTop20ByKeyPrefixIsNullOrderByIdAsc()) {
            if (passwordEncoder.matches(rawKey, apiKey.getKeyHash())) {
                apiKey.setKeyPrefix(prefix);
                apiKeyRepository.save(apiKey);
                log.info("Migrated legacy API key {} to prefixed lookup", apiKey.getId());
                return Optional.of(new ApiKeyPrincipal(apiKey.getId(), apiKey.getUserId()));
            }
        }
        return Optional.empty();
    }

    private void touch(Long keyId, String clientIp, long now) {
        Long last = lastTouch.get(keyId);
        if (last != null && now - last < TOUCH_INTERVAL_MILLIS) {
            return;
        }
        lastTouch.put(keyId, now);
        String ip = clientIp != null && clientIp.length() > 45 ? clientIp.substring(0, 45) : clientIp;
        apiKeyRepository.touch(keyId, LocalDateTime.now(), ip);
    }

    private void trimCaches(long now) {
        if (verifiedCache.size() > MAX_CACHE_ENTRIES) {
            verifiedCache.values().removeIf(c -> c.expiresAt() <= now);
        }
        if (negativeCache.size() > MAX_CACHE_ENTRIES) {
            negativeCache.values().removeIf(until -> until <= now);
            if (negativeCache.size() > MAX_CACHE_ENTRIES) {
                negativeCache.clear();
            }
        }
    }

    private String lookupPrefix(String rawKey) {
        return rawKey.substring(0, Math.min(LOOKUP_PREFIX_LEN, rawKey.length()));
    }

    private String generateRawKey() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return KEY_PREFIX + encoded;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ApiKeyResponse toResponse(ApiKey apiKey) {
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
}
