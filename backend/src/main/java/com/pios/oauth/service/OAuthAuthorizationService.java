package com.pios.oauth.service;

import com.pios.oauth.AuthorizationRedirectException;
import com.pios.oauth.OAuthException;
import com.pios.oauth.OAuthNotFoundException;
import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthScopes;
import com.pios.oauth.OAuthTokens;
import com.pios.oauth.RedirectUris;
import com.pios.oauth.domain.OAuthAuthorizationCode;
import com.pios.oauth.domain.OAuthAuthorizationRequest;
import com.pios.oauth.dto.AuthorizationRequestView;
import com.pios.oauth.repository.OAuthAuthorizationCodeRepository;
import com.pios.oauth.repository.OAuthAuthorizationRequestRepository;
import com.pios.oauth.repository.OAuthGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuthAuthorizationService {

    private static final int MAX_STATE_LENGTH = 2048;

    private final OAuthProperties properties;
    private final OAuthClientService clientService;
    private final OAuthAuthorizationRequestRepository requestRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final OAuthGrantRepository grantRepository;

    /** Parameters of GET /api/oauth/authorize. */
    public record AuthorizeParams(String responseType, String clientId, String redirectUri, String codeChallenge,
                                  String codeChallengeMethod, String state, String scope, String resource) {
    }

    /**
     * Validates the request and stores it pending consent.
     * <p>Deliberately NOT transactional: resolving a CIMD client performs an outbound HTTPS fetch that must not
     * hold a JDBC connection. The final save runs in the repository's own short transaction.
     *
     * @return URL of the frontend consent page
     * @throws OAuthException                 client_id / redirect_uri invalid, or the client has never been
     *                                        authorized by anyone → show error page (no redirect)
     * @throws AuthorizationRedirectException other errors for a known client → redirect to the client
     */
    public String authorize(AuthorizeParams p, String clientIp) {
        ResolvedClient client = clientService.resolve(p.clientId(), clientIp);
        if (p.redirectUri() == null || p.redirectUri().isBlank()) {
            throw OAuthException.invalidRequest("redirect_uri is required");
        }
        if (!RedirectUris.isValid(p.redirectUri()) || !RedirectUris.matchesAny(p.redirectUri(), client.redirectUris())) {
            throw OAuthException.invalidRequest("redirect_uri is not registered for this client");
        }
        ErrorReporter fail = new ErrorReporter(client.clientId(), p.redirectUri(), p.state());
        String redirectUri = p.redirectUri();
        String state = p.state();
        if (state != null && state.length() > MAX_STATE_LENGTH) {
            throw fail.with("invalid_request", "state too long", false);
        }

        if (!"code".equals(p.responseType())) {
            throw fail.with("unsupported_response_type", "response_type must be code", true);
        }
        if (p.codeChallenge() == null || p.codeChallenge().isBlank()) {
            throw fail.with("invalid_request", "code_challenge is required (PKCE)", true);
        }
        if (!"S256".equals(p.codeChallengeMethod())) {
            throw fail.with("invalid_request", "code_challenge_method must be S256", true);
        }
        if (!OAuthTokens.isValidCodeChallenge(p.codeChallenge())) {
            throw fail.with("invalid_request", "malformed code_challenge", true);
        }
        String scope;
        try {
            scope = OAuthScopes.normalize(p.scope());
        } catch (OAuthException e) {
            throw fail.with("invalid_scope", e.getMessage(), true);
        }
        if (scope.isEmpty()) {
            throw fail.with("invalid_scope", "no supported scope requested", true);
        }
        String resource = p.resource();
        if (resource != null && !resource.isBlank() && !properties.getResource().equals(resource)) {
            throw fail.with("invalid_target", "unknown resource", true);
        }

        OAuthAuthorizationRequest request = OAuthAuthorizationRequest.builder()
                .id(OAuthTokens.randomToken(32))
                .clientId(client.clientId())
                .clientName(client.clientName())
                .redirectUri(redirectUri)
                .codeChallenge(p.codeChallenge())
                .scope(scope)
                .state(state)
                .resource(properties.getResource())
                .expiresAt(Instant.now().plusSeconds(properties.getRequestTtlSeconds()))
                .build();
        requestRepository.save(request);

        return properties.getFrontendBaseUrl() + "/oauth/consent?request_id="
                + URLEncoder.encode(request.getId(), StandardCharsets.UTF_8);
    }

    /**
     * Error redirects only go to clients some user has authorized before (has/had a grant). Otherwise anyone
     * could register an https redirect_uri and use /authorize as an open redirector → plain error page instead.
     */
    private final class ErrorReporter {
        private final String clientId;
        private final String redirectUri;
        private final String state;

        ErrorReporter(String clientId, String redirectUri, String state) {
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.state = state;
        }

        RuntimeException with(String error, String description, boolean includeState) {
            if (!grantRepository.existsByClientId(clientId)) {
                return new OAuthException(error, description);
            }
            return new AuthorizationRedirectException(
                    errorRedirect(redirectUri, error, description, includeState ? state : null), error);
        }
    }

    @Transactional(readOnly = true)
    public AuthorizationRequestView getRequest(String requestId) {
        OAuthAuthorizationRequest r = requestRepository.findById(requestId)
                .filter(x -> x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new OAuthNotFoundException("Authorization request not found or expired"));
        return AuthorizationRequestView.builder()
                .requestId(r.getId())
                .clientName(r.getClientName())
                .clientId(r.getClientId())
                .redirectUri(r.getRedirectUri())
                .redirectHost(RedirectUris.displayTarget(r.getRedirectUri()))
                .scopes(OAuthScopes.toList(r.getScope()))
                .expiresAt(r.getExpiresAt())
                .build();
    }

    /** Consumes the pending request; on approval issues a single-use code bound to the user. */
    @Transactional
    public String decide(String requestId, Long userId, boolean approve) {
        OAuthAuthorizationRequest r = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new OAuthNotFoundException("Authorization request not found or expired"));
        requestRepository.delete(r);
        if (!r.getExpiresAt().isAfter(Instant.now())) {
            throw new OAuthNotFoundException("Authorization request not found or expired");
        }
        if (!approve) {
            return errorRedirect(r.getRedirectUri(), "access_denied", null, r.getState());
        }
        String code = OAuthTokens.randomToken(32);
        codeRepository.save(OAuthAuthorizationCode.builder()
                .codeHash(OAuthTokens.sha256Hex(code))
                .userId(userId)
                .clientId(r.getClientId())
                .clientName(r.getClientName())
                .redirectUri(r.getRedirectUri())
                .codeChallenge(r.getCodeChallenge())
                .scope(r.getScope())
                .resource(r.getResource())
                .expiresAt(Instant.now().plusSeconds(properties.getCodeTtlSeconds()))
                .build());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", code);
        if (r.getState() != null) {
            params.put("state", r.getState());
        }
        params.put("iss", properties.getIssuer());
        return appendQuery(r.getRedirectUri(), params);
    }

    String errorRedirect(String redirectUri, String error, String description, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("error", error);
        if (description != null) {
            params.put("error_description", description);
        }
        if (state != null) {
            params.put("state", state);
        }
        params.put("iss", properties.getIssuer());
        return appendQuery(redirectUri, params);
    }

    static String appendQuery(String uri, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(uri);
        boolean hasQuery = uri.indexOf('?') >= 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(hasQuery ? '&' : '?');
            hasQuery = true;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
