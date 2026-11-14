package com.pios.service;

import com.pios.domain.RefreshToken;
import com.pios.domain.User;
import com.pios.dto.AuthRequest;
import com.pios.dto.AuthResponse;
import com.pios.dto.UserDto;
import com.pios.repository.RefreshTokenRepository;
import com.pios.repository.UserRepository;
import com.pios.security.InvalidCredentialsException;
import com.pios.security.JwtUtil;
import com.pios.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    /** BCrypt hash of a random value; checked for unknown emails so response timing doesn't reveal accounts. */
    private volatile String dummyPasswordHash;

    @Value("${pios.registration-enabled:false}")
    private boolean registrationEnabled;

    @Transactional
    public AuthResponse register(AuthRequest request) {
        if (!registrationEnabled) {
            throw new AccessDeniedException("Registration is disabled");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .build();
        user = userRepository.save(user);

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail());
        String refreshToken = createAndSaveRefreshToken(user);

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .user(toDto(user))
                .build();
    }

    @Transactional
    public AuthResponse login(AuthRequest request, String clientIp) {
        String email = request.getEmail();
        loginAttemptService.checkAllowed(email, clientIp);

        Optional<User> found = userRepository.findByEmail(email);
        boolean valid;
        if (found.isPresent()) {
            valid = passwordEncoder.matches(request.getPassword(), found.get().getPasswordHash());
        } else {
            passwordEncoder.matches(request.getPassword(), dummyHash());
            valid = false;
        }
        if (!valid) {
            loginAttemptService.onFailure(email, clientIp);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        loginAttemptService.onSuccess(email, clientIp);
        User user = found.get();

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail());
        String refreshToken = createAndSaveRefreshToken(user);

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .user(toDto(user))
                .build();
    }

    private String dummyHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            dummyPasswordHash = hash;
        }
        return hash;
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        String tokenHash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new InvalidCredentialsException("Refresh token expired");
        }

        User user = stored.getUser();

        // Rotation: delete old, create new
        refreshTokenRepository.delete(stored);

        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getEmail());
        String newRefreshToken = createAndSaveRefreshToken(user);

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(toDto(user))
                .build();
    }

    /** Hourly purge of expired web-login refresh tokens. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.purgeExpired(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens", deleted);
        }
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public UserDto me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toDto(user);
    }

    private String createAndSaveRefreshToken(User user) {
        String rawToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());
        String tokenHash = hashToken(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshExpirationSeconds()))
                .build();
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
