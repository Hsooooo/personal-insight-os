package com.pios.security;

import com.pios.oauth.OAuthProperties;
import com.pios.oauth.service.OAuthTokenService;
import com.pios.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end check of the security chain (filter + McpAccessPolicy) against stub endpoints. */
@WebMvcTest(controllers = SecurityConfigWebTest.StubController.class)
@Import({SecurityConfig.class, InternalSecretVerifier.class, SecurityConfigWebTest.StubController.class})
@TestPropertySource(properties = {
        "pios.mcp-internal-secret=" + SecurityConfigWebTest.SECRET,
        "cors.allowed-origins=http://localhost:5173"
})
class SecurityConfigWebTest {

    static final String SECRET = "internal-secret-internal-secret-0123456789";

    @RestController
    static class StubController {
        @GetMapping("/api/goals")
        String goals(@AuthenticationPrincipal Long userId) {
            return "goals:" + userId;
        }

        @PostMapping("/api/goals")
        String createGoal() {
            return "created";
        }

        @GetMapping("/api/settings/llm-providers")
        String providers() {
            return "providers";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "up";
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private ApiKeyService apiKeyService;
    @MockBean
    private OAuthTokenService oAuthTokenService;
    @MockBean
    private OAuthProperties oAuthProperties;

    @BeforeEach
    void setUp() {
        when(jwtUtil.parseAccessToken(any())).thenReturn(Optional.empty());
        when(oAuthTokenService.verifyAccessToken(any())).thenReturn(Optional.empty());
        when(apiKeyService.authenticate(any(), any())).thenReturn(Optional.empty());
        when(apiKeyService.authenticate(eq("pios_key"), any()))
                .thenReturn(Optional.of(new ApiKeyService.ApiKeyPrincipal(1L, 7L)));
        when(oAuthTokenService.verifyAccessToken("read.token")).thenReturn(Optional.of(
                new OAuthTokenService.AccessTokenInfo(8L, "mcp:read", "c", 1L, Instant.now().plusSeconds(60), "aud")));
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mvc.perform(get("/api/goals")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void mcpPrincipalAllowedOnGoalsGet() throws Exception {
        mvc.perform(get("/api/goals").header("X-API-Key", "pios_key"))
                .andExpect(status().isOk())
                .andExpect(content().string("goals:7"));
    }

    @Test
    void mcpPrincipalBlockedFromLlmProviders() throws Exception {
        mvc.perform(get("/api/settings/llm-providers").header("Authorization", "Bearer pios_key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void oauthTokenWithoutSecretIs401() throws Exception {
        mvc.perform(get("/api/goals").header("Authorization", "Bearer read.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oauthReadTokenCanReadButNotWrite() throws Exception {
        mvc.perform(get("/api/goals").header("Authorization", "Bearer read.token")
                        .header(InternalSecretVerifier.HEADER, SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string("goals:8"));
        mvc.perform(post("/api/goals").header("Authorization", "Bearer read.token")
                        .header(InternalSecretVerifier.HEADER, SECRET))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiKeyWithWriteScopeCanWrite() throws Exception {
        mvc.perform(post("/api/goals").header("X-API-Key", "pios_key"))
                .andExpect(status().isOk());
    }
}
