package com.pios.service;

import com.pios.domain.RefreshToken;
import com.pios.domain.User;
import com.pios.dto.AuthRequest;
import com.pios.dto.AuthResponse;
import com.pios.dto.UserDto;
import com.pios.repository.RefreshTokenRepository;
import com.pios.repository.UserRepository;
import com.pios.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(AuthRequest request) {
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
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail());
        String refreshToken = createAndSaveRefreshToken(user);

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .user(toDto(user))
                .build();
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String tokenHash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired");
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
                .expiresAt(Instant.now().plusSeconds(604800)) // 7 days
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
