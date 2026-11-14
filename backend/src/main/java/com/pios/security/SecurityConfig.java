package com.pios.security;

import com.pios.oauth.OAuthProperties;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuthProperties oAuthProperties;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 컨테이너의 /error 포워드는 인증 필터를 다시 타지 않으므로 허용해야 원래 상태코드(403 등)가 유지된다
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // OAuth 2.1 Authorization Server public endpoints
                .requestMatchers(HttpMethod.GET, "/.well-known/oauth-authorization-server",
                        "/.well-known/oauth-authorization-server/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/oauth/authorize").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/oauth/register", "/api/oauth/token",
                        "/api/oauth/revoke").permitAll()
                // internal-only: guarded by X-PIOS-Internal-Secret inside the controller (and blocked at Caddy)
                .requestMatchers(HttpMethod.POST, "/api/oauth/introspect").permitAll()
                // everything else: authenticated; ROLE_MCP principals limited to the MCP allowlist (403 otherwise)
                .anyRequest().access(new McpAccessPolicy())
            )
            // 미인증 요청은 401로 응답해야 프론트가 refresh 흐름을 탈 수 있다 (기본값은 403)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                // sendError(403) 대신 직접 기록: /error 포워드 시 익명으로 재평가되어 401로 바뀌는 문제 방지
                .accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"message\":\"Forbidden\",\"data\":null}");
                }))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** The filter only runs inside the security chain, not additionally as a servlet filter. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Public OAuth endpoints may be called from browser-based MCP clients on any origin (no cookies).
        CorsConfiguration publicOAuth = new CorsConfiguration();
        publicOAuth.setAllowedOriginPatterns(List.of("*"));
        publicOAuth.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        publicOAuth.setAllowedHeaders(List.of("Content-Type", "Authorization", "MCP-Protocol-Version"));
        publicOAuth.setAllowCredentials(false);
        for (String path : List.of("/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/**",
                "/api/oauth/token", "/api/oauth/register", "/api/oauth/revoke")) {
            source.registerCorsConfiguration(path, publicOAuth);
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** PIOS_CORS_ALLOWED_ORIGINS plus the public/frontend base URL origins. */
    List<String> allowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        if (corsAllowedOrigins != null) {
            for (String o : corsAllowedOrigins.split(",")) {
                String trimmed = o.trim();
                while (trimmed.endsWith("/")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                if (!trimmed.isEmpty() && !trimmed.equals("*")) {
                    origins.add(trimmed);
                }
            }
        }
        addOrigin(origins, oAuthProperties.getIssuer());
        addOrigin(origins, oAuthProperties.getFrontendBaseUrl());
        return new ArrayList<>(origins);
    }

    private static void addOrigin(Set<String> origins, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() != null && uri.getHost() != null) {
                origins.add(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : ""));
            }
        } catch (IllegalArgumentException ignored) {
            // invalid URL: ignore
        }
    }
}
