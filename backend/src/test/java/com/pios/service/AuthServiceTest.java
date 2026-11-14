package com.pios.service;

import com.pios.domain.User;
import com.pios.dto.AuthRequest;
import com.pios.repository.RefreshTokenRepository;
import com.pios.repository.UserRepository;
import com.pios.security.InvalidCredentialsException;
import com.pios.security.JwtUtil;
import com.pios.security.LoginAttemptService;
import com.pios.security.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    private static AuthRequest request(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void registerIsRejectedWhenRegistrationDisabled() {
        assertThatThrownBy(() -> authService.register(request("new@example.com", "password123")))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void wrongPasswordIsInvalidCredentialsAndRecordsFailure() {
        User user = User.builder().id(1L).email("me@example.com").passwordHash("hash").build();
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request("me@example.com", "bad"), "1.2.3.4"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).onFailure("me@example.com", "1.2.3.4");
    }

    @Test
    void unknownEmailStillRunsPasswordCheck() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("dummy");

        assertThatThrownBy(() -> authService.login(request("ghost@example.com", "pw"), "1.2.3.4"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches("pw", "dummy");
        verify(loginAttemptService).onFailure("ghost@example.com", "1.2.3.4");
    }

    @Test
    void throttledLoginNeverChecksPassword() {
        doThrow(new TooManyRequestsException("slow down", 60))
                .when(loginAttemptService).checkAllowed("me@example.com", "1.2.3.4");

        assertThatThrownBy(() -> authService.login(request("me@example.com", "pw"), "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void successfulLoginUsesConfiguredRefreshTtl() {
        User user = User.builder().id(1L).email("me@example.com").passwordHash("hash").build();
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("good", "hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "me@example.com")).thenReturn("access");
        when(jwtUtil.generateRefreshToken(1L, "me@example.com")).thenReturn("refresh");
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(1234L);

        var result = authService.login(request("me@example.com", "good"), "1.2.3.4");

        assertThat(result.getToken()).isEqualTo("access");
        verify(loginAttemptService).onSuccess("me@example.com", "1.2.3.4");
        verify(refreshTokenRepository).save(argThat(t ->
                t.getExpiresAt().isAfter(java.time.Instant.now().plusSeconds(1200))
                        && t.getExpiresAt().isBefore(java.time.Instant.now().plusSeconds(1300))));
    }

    @Test
    void invalidRefreshTokenIsInvalidCredentials() {
        when(jwtUtil.validateRefreshToken("x")).thenReturn(false);
        assertThatThrownBy(() -> authService.refresh("x")).isInstanceOf(InvalidCredentialsException.class);
    }
}
