package com.pios.service;

import com.pios.domain.ApiKey;
import com.pios.domain.User;
import com.pios.dto.ApiKeyResponse;
import com.pios.repository.ApiKeyRepository;
import com.pios.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    private static final String KEY_PREFIX = "pios_";
    private static final int KEY_BYTES = 32;

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

    @Transactional(readOnly = true)
    public User validateApiKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return null;
        }

        // 키 해시로 직접 조회는 불가능하므로 모든 키를 검사
        // 실제 프로덕션에서는 더 효율적인 방식 필요
        List<ApiKey> keys = apiKeyRepository.findAll();
        for (ApiKey apiKey : keys) {
            if (passwordEncoder.matches(rawKey, apiKey.getKeyHash())) {
                return userRepository.findById(apiKey.getUserId()).orElse(null);
            }
        }
        return null;
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
