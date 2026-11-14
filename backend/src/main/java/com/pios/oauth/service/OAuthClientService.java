package com.pios.oauth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pios.oauth.OAuthException;
import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthScopes;
import com.pios.oauth.OAuthTokens;
import com.pios.oauth.RedirectUris;
import com.pios.oauth.domain.OAuthClient;
import com.pios.oauth.dto.ClientRegistrationRequest;
import com.pios.oauth.repository.OAuthClientRepository;
import com.pios.security.SlidingWindowRateLimiter;
import com.pios.security.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OAuthClientService {

    static final List<String> SUPPORTED_GRANT_TYPES = List.of("authorization_code", "refresh_token");
    private static final int MAX_REDIRECT_URIS = 10;
    private static final int MAX_CLIENT_NAME = 255;
    private static final long CIMD_TTL_MILLIS = 3_600_000;
    private static final long CIMD_FAILURE_TTL_MILLIS = 60_000;
    private static final int CIMD_CACHE_MAX = 500;

    private final OAuthClientRepository clientRepository;
    private final ClientMetadataDocumentFetcher fetcher;
    private final ObjectMapper objectMapper;
    private final SlidingWindowRateLimiter registrationLimiter;
    /** Outbound CIMD fetches per client IP (cache misses only). */
    private final SlidingWindowRateLimiter cimdFetchLimiter = new SlidingWindowRateLimiter(10, 60_000L);

    private record CacheEntry(ResolvedClient client, String error, long expiresAt) {
    }

    private final Map<String, CacheEntry> cimdCache = new ConcurrentHashMap<>();

    public OAuthClientService(OAuthClientRepository clientRepository,
                              ClientMetadataDocumentFetcher fetcher,
                              ObjectMapper objectMapper,
                              OAuthProperties properties) {
        this.clientRepository = clientRepository;
        this.fetcher = fetcher;
        this.objectMapper = objectMapper;
        this.registrationLimiter = new SlidingWindowRateLimiter(
                Math.max(1, properties.getRegistrationRateLimitPerHour()), 3_600_000L);
    }

    // ------------------------------------------------------------------ DCR

    @Transactional
    public Map<String, Object> register(ClientRegistrationRequest req, String clientIp) {
        if (!registrationLimiter.tryAcquire(clientIp)) {
            throw new TooManyRequestsException("Too many client registrations", registrationLimiter.retryAfterSeconds(clientIp));
        }
        if (req == null) {
            throw new OAuthException("invalid_client_metadata", "Missing client metadata");
        }
        String authMethod = req.getTokenEndpointAuthMethod() == null ? "none" : req.getTokenEndpointAuthMethod();
        if (!"none".equals(authMethod)) {
            throw new OAuthException("invalid_client_metadata",
                    "Only public clients are supported (token_endpoint_auth_method=none)");
        }
        List<String> redirectUris = validateRedirectUris(req.getRedirectUris(), "invalid_redirect_uri");

        List<String> grantTypes = req.getGrantTypes() == null || req.getGrantTypes().isEmpty()
                ? SUPPORTED_GRANT_TYPES : List.copyOf(new LinkedHashSet<>(req.getGrantTypes()));
        if (!SUPPORTED_GRANT_TYPES.containsAll(grantTypes) || !grantTypes.contains("authorization_code")) {
            throw new OAuthException("invalid_client_metadata", "Unsupported grant_types");
        }
        if (req.getResponseTypes() != null && !req.getResponseTypes().isEmpty()
                && !req.getResponseTypes().equals(List.of("code"))) {
            throw new OAuthException("invalid_client_metadata", "Only response_type=code is supported");
        }
        String scope;
        try {
            scope = req.getScope() == null || req.getScope().isBlank() ? null : OAuthScopes.normalize(req.getScope());
        } catch (OAuthException e) {
            throw new OAuthException("invalid_client_metadata", e.getMessage());
        }
        String clientName = sanitizeName(req.getClientName());

        OAuthClient client = OAuthClient.builder()
                .clientId("mcp_" + OAuthTokens.randomToken(24))
                .clientName(clientName)
                .redirectUris(toJson(redirectUris))
                .grantTypes(String.join(" ", grantTypes))
                .scope(scope)
                .tokenEndpointAuthMethod("none")
                .registrationIp(clientIp != null && clientIp.length() > 45 ? clientIp.substring(0, 45) : clientIp)
                .build();
        client = clientRepository.save(client);
        log.info("Registered OAuth client {} ({})", client.getClientId(), clientName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", client.getClientId());
        body.put("client_id_issued_at", (client.getCreatedAt() != null
                ? client.getCreatedAt() : java.time.Instant.now()).getEpochSecond());
        if (clientName != null) {
            body.put("client_name", clientName);
        }
        body.put("redirect_uris", redirectUris);
        body.put("grant_types", grantTypes);
        body.put("response_types", List.of("code"));
        body.put("token_endpoint_auth_method", "none");
        if (scope != null) {
            body.put("scope", scope);
        }
        return body;
    }

    static List<String> validateRedirectUris(List<String> uris, String errorCode) {
        if (uris == null || uris.isEmpty()) {
            throw new OAuthException(errorCode, "redirect_uris is required");
        }
        if (uris.size() > MAX_REDIRECT_URIS) {
            throw new OAuthException(errorCode, "Too many redirect_uris");
        }
        for (String uri : uris) {
            if (!RedirectUris.isValid(uri)) {
                throw new OAuthException(errorCode,
                        "redirect_uri must be https, http://localhost / http://127.0.0.1, or a private-use app scheme: " + abbreviate(uri));
            }
        }
        return List.copyOf(new LinkedHashSet<>(uris));
    }

    // ------------------------------------------------------------ resolution

    /**
     * Resolves a client_id (registered id or CIMD https URL).
     * @throws OAuthException invalid_client when unknown or metadata document is invalid
     */
    public ResolvedClient resolve(String clientId, String clientIp) {
        if (clientId == null || clientId.isBlank() || clientId.length() > 2048) {
            throw OAuthException.invalidClient("Missing or invalid client_id");
        }
        if (ClientMetadataDocumentFetcher.isUrlClientId(clientId)) {
            return resolveMetadataDocument(clientId, clientIp == null ? "unknown" : clientIp);
        }
        OAuthClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> OAuthException.invalidClient("Unknown client_id"));
        return new ResolvedClient(client.getClientId(), client.getClientName(),
                fromJson(client.getRedirectUris()), false);
    }

    private ResolvedClient resolveMetadataDocument(String clientId, String clientIp) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cimdCache.get(clientId);
        if (cached != null && cached.expiresAt() > now) {
            if (cached.client() != null) {
                return cached.client();
            }
            throw OAuthException.invalidClient(cached.error());
        }
        if (!cimdFetchLimiter.tryAcquire(clientIp)) {
            throw new TooManyRequestsException("Too many client metadata lookups",
                    cimdFetchLimiter.retryAfterSeconds(clientIp));
        }
        try {
            ResolvedClient client = parseMetadataDocument(clientId, fetcher.fetch(clientId));
            putCache(clientId, new CacheEntry(client, null, now + CIMD_TTL_MILLIS), now);
            return client;
        } catch (IOException | OAuthException e) {
            String message = "Client metadata document rejected: " + e.getMessage();
            log.warn("CIMD fetch failed for {}: {}", abbreviate(clientId), e.getMessage());
            putCache(clientId, new CacheEntry(null, message, now + CIMD_FAILURE_TTL_MILLIS), now);
            throw OAuthException.invalidClient(message);
        }
    }

    private void putCache(String key, CacheEntry entry, long now) {
        if (cimdCache.size() >= CIMD_CACHE_MAX) {
            cimdCache.values().removeIf(e -> e.expiresAt() <= now);
            if (cimdCache.size() >= CIMD_CACHE_MAX) {
                cimdCache.clear();
            }
        }
        cimdCache.put(key, entry);
    }

    static ResolvedClient parseMetadataDocument(String clientId, JsonNode doc) {
        if (doc == null || !doc.isObject()) {
            throw new OAuthException("invalid_client", "metadata document must be a JSON object");
        }
        if (!clientId.equals(doc.path("client_id").asText(null))) {
            throw new OAuthException("invalid_client", "metadata document client_id does not match URL");
        }
        String authMethod = doc.path("token_endpoint_auth_method").asText("none");
        if (!"none".equals(authMethod)) {
            throw new OAuthException("invalid_client", "only token_endpoint_auth_method=none is supported");
        }
        JsonNode grantTypes = doc.get("grant_types");
        if (grantTypes != null && grantTypes.isArray()) {
            boolean hasCode = false;
            for (JsonNode g : grantTypes) {
                hasCode |= "authorization_code".equals(g.asText());
            }
            if (!hasCode) {
                throw new OAuthException("invalid_client", "authorization_code grant not declared");
            }
        }
        JsonNode uris = doc.get("redirect_uris");
        if (uris == null || !uris.isArray()) {
            throw new OAuthException("invalid_client", "redirect_uris missing");
        }
        List<String> list = new ArrayList<>();
        for (JsonNode u : uris) {
            if (!u.isTextual()) {
                throw new OAuthException("invalid_client", "redirect_uris must be strings");
            }
            list.add(u.asText());
        }
        List<String> redirectUris = validateRedirectUris(list, "invalid_client");
        String name = sanitizeName(doc.path("client_name").asText(null));
        if (name == null) {
            name = URI.create(clientId).getHost();
        }
        return new ResolvedClient(clientId, name, redirectUris, true);
    }

    static String sanitizeName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_CLIENT_NAME ? cleaned.substring(0, MAX_CLIENT_NAME) : cleaned;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new OAuthException("server_error", "Corrupt client registration", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
