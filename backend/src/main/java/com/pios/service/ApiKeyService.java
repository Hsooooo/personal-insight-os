package com.pios.service;

import com.pios.domain.ApiKey;
import com.pios.domain.User;
import com.pios.dto.ApiKeyResponse;
import com.pios.repository.ApiKeyRepository;
import com.pios.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String KEY_PREFIX = "pios_";
    private static final int KEY_BYTES = 32;
    private static final int LOOKUP_PREFIX_LEN = 16;

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
    }

    @Transactional
    public User validateApiKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return null;
        }

        String prefix = lookupPrefix(rawKey);
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefix(prefix);
        if (candidates.isEmpty()) {
            // Legacy rows without key_prefix — one-time fallback scan
            candidates = apiKeyRepository.findAll().stream()
                    .filter(k -> k.getKeyPrefix() == null || k.getKeyPrefix().isBlank())
                    .toList();
        }

        for (ApiKey apiKey : candidates) {
            if (passwordEncoder.matches(rawKey, apiKey.getKeyHash())) {
                if (apiKey.getKeyPrefix() == null || apiKey.getKeyPrefix().isBlank()) {
                    apiKey.setKeyPrefix(prefix);
                    apiKeyRepository.save(apiKey);
                }
                return userRepository.findById(apiKey.getUserId()).orElse(null);
            }
        }
        return null;
    }

    private String lookupPrefix(String rawKey) {
        return rawKey.substring(0, Math.min(LOOKUP_PREFIX_LEN, rawKey.length()));
    }

    private String generateRawKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return KEY_PREFIX + encoded;
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
