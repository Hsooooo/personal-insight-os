package com.pios.security;

import com.pios.oauth.service.OAuthTokenService;
import com.pios.service.ApiKeyService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves one of three principal types (principal is always the {@code Long} userId):
 * <ol>
 *   <li>web-login JWT (type=access) → {@code ROLE_USER}</li>
 *   <li>OAuth access token (typ=oauth_access) → only with aud == MCP resource, active grant and a valid
 *       {@code X-PIOS-Internal-Secret} header; authorities {@code ROLE_MCP} + {@code SCOPE_*} from the token</li>
 *   <li>{@code pios_} API key ({@code X-API-Key} or {@code Authorization: Bearer pios_...}) →
 *       {@code ROLE_MCP, SCOPE_mcp:read, SCOPE_mcp:write}</li>
 * </ol>
 * Endpoint restrictions for ROLE_MCP are enforced by {@link McpAccessPolicy}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ApiKeyService apiKeyService;
    private final OAuthTokenService oAuthTokenService;
    private final InternalSecretVerifier internalSecretVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String bearer = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim() : null;

        if (bearer != null && !bearer.isEmpty()) {
            if (ApiKeyService.looksLikeApiKey(bearer)) {
                authenticateApiKey(bearer, request);
            } else {
                Optional<Claims> userToken = jwtUtil.parseAccessToken(bearer);
                if (userToken.isPresent()) {
                    authenticateUser(userToken.get());
                } else if (internalSecretVerifier.matches(request)) {
                    oAuthTokenService.verifyAccessToken(bearer).ifPresent(this::authenticateOAuth);
                }
            }
        } else {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey != null && !apiKey.isBlank()) {
                authenticateApiKey(apiKey.trim(), request);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateUser(Claims claims) {
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return;
        }
        setAuthentication(userId, List.of(new SimpleGrantedAuthority(PiosAuthorities.ROLE_USER)));
    }

    private void authenticateOAuth(OAuthTokenService.AccessTokenInfo info) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(PiosAuthorities.ROLE_MCP));
        if (info.scope() != null) {
            for (String scope : info.scope().trim().split("\\s+")) {
                if (!scope.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }
        }
        setAuthentication(info.userId(), authorities);
    }

    private void authenticateApiKey(String rawKey, HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request, internalSecretVerifier.matches(request));
        apiKeyService.authenticate(rawKey, ip).ifPresent(key ->
                setAuthentication(key.userId(), List.of(
                        new SimpleGrantedAuthority(PiosAuthorities.ROLE_MCP),
                        new SimpleGrantedAuthority(PiosAuthorities.SCOPE_MCP_READ),
                        new SimpleGrantedAuthority(PiosAuthorities.SCOPE_MCP_WRITE))));
    }

    private static void setAuthentication(Long userId, List<GrantedAuthority> authorities) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
