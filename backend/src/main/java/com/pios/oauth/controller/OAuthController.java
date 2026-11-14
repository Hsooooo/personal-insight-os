package com.pios.oauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pios.oauth.AuthorizationRedirectException;
import com.pios.oauth.OAuthException;
import com.pios.oauth.dto.ClientRegistrationRequest;
import com.pios.oauth.service.OAuthAuthorizationService;
import com.pios.oauth.service.OAuthClientService;
import com.pios.oauth.service.OAuthTokenService;
import com.pios.security.ClientIpResolver;
import com.pios.security.InternalSecretVerifier;
import com.pios.security.SlidingWindowRateLimiter;
import com.pios.security.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public OAuth 2.1 endpoints. Responses are raw RFC JSON (not ApiResponse).
 * /introspect is internal-only (shared secret header; blocked at Caddy).
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthAuthorizationService authorizationService;
    private final OAuthClientService clientService;
    private final OAuthTokenService tokenService;
    private final InternalSecretVerifier internalSecretVerifier;
    private final ObjectMapper objectMapper;
    /** Public, unauthenticated endpoint: per-IP cap on /authorize. */
    private final SlidingWindowRateLimiter authorizeLimiter = new SlidingWindowRateLimiter(60, 60_000L);

    @GetMapping("/authorize")
    public ResponseEntity<String> authorize(@RequestParam(name = "response_type", required = false) String responseType,
                                            @RequestParam(name = "client_id", required = false) String clientId,
                                            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                                            @RequestParam(name = "code_challenge_method", required = false) String method,
                                            @RequestParam(name = "state", required = false) String state,
                                            @RequestParam(name = "scope", required = false) String scope,
                                            @RequestParam(name = "resource", required = false) String resource,
                                            HttpServletRequest http) {
        String clientIp = ClientIpResolver.resolve(http);
        try {
            if (!authorizeLimiter.tryAcquire(clientIp)) {
                throw new TooManyRequestsException("Too many authorization requests",
                        authorizeLimiter.retryAfterSeconds(clientIp));
            }
            String consentUrl = authorizationService.authorize(new OAuthAuthorizationService.AuthorizeParams(
                    responseType, clientId, redirectUri, codeChallenge, method, state, scope, resource), clientIp);
            return redirect(consentUrl);
        } catch (AuthorizationRedirectException e) {
            return redirect(e.getRedirectUrl());
        } catch (OAuthException e) {
            // never redirect here: show an error page instead
            return errorPage(HttpStatus.BAD_REQUEST, e.getError(), e.getMessage(), null);
        } catch (TooManyRequestsException e) {
            return errorPage(HttpStatus.TOO_MANY_REQUESTS, "slow_down", e.getMessage(), e.getRetryAfterSeconds());
        }
    }

    private static ResponseEntity<String> errorPage(HttpStatus status, String error, String description, Long retryAfter) {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Authorization error</title></head>"
                + "<body><h1>Authorization error</h1><p>" + HtmlUtils.htmlEscape(String.valueOf(error)) + ": "
                + HtmlUtils.htmlEscape(String.valueOf(description)) + "</p></body></html>";
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore());
        if (retryAfter != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        return builder.body(html);
    }

    private static ResponseEntity<String> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody(required = false) ClientRegistrationRequest request,
                                                        HttpServletRequest http) {
        Map<String, Object> body = clientService.register(request, ClientIpResolver.resolve(http));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(HttpServletRequest http) {
        Map<String, String> params = formParams(http);
        String grantType = params.get("grant_type");
        Map<String, Object> body;
        if ("authorization_code".equals(grantType)) {
            body = tokenService.exchangeCode(params.get("code"), params.get("redirect_uri"), params.get("client_id"),
                    params.get("code_verifier"), params.get("resource"));
        } else if ("refresh_token".equals(grantType)) {
            body = tokenService.refresh(params.get("refresh_token"), params.get("client_id"), params.get("scope"));
        } else if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("grant_type is required");
        } else {
            throw new OAuthException("unsupported_grant_type", "Unsupported grant_type");
        }
        return ResponseEntity.ok().headers(noStore()).body(body);
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(HttpServletRequest http) {
        Map<String, String> params = formParams(http);
        tokenService.revoke(params.get("token"), params.get("client_id"));
        return ResponseEntity.ok().headers(noStore()).build();
    }

    @PostMapping("/introspect")
    public ResponseEntity<Map<String, Object>> introspect(HttpServletRequest request) throws IOException {
        if (!internalSecretVerifier.matches(request)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "invalid_client");
            err.put("error_description", "Internal secret required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(noStore()).body(err);
        }
        String token;
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("json")) {
            if (request.getQueryString() != null) {
                throw OAuthException.invalidRequest("Parameters must be sent in the request body");
            }
            JsonNode node = objectMapper.readTree(request.getInputStream());
            token = node != null && node.hasNonNull("token") ? node.get("token").asText() : null;
        } else {
            token = formParams(request).get("token");
        }
        return ResponseEntity.ok().headers(noStore())
                .body(tokenService.introspect(token, ClientIpResolver.resolve(request, true)));
    }

    /**
     * Form-body parameters only (RFC 6749 §3.2): parameters in the query string and repeated parameters
     * are rejected with invalid_request.
     */
    static Map<String, String> formParams(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            throw OAuthException.invalidRequest("Parameters must be sent in the application/x-www-form-urlencoded body");
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            if (e.getValue().length > 1) {
                throw OAuthException.invalidRequest("Repeated parameter: " + e.getKey());
            }
            params.put(e.getKey(), e.getValue().length == 1 ? e.getValue()[0] : null);
        }
        return params;
    }

    private static HttpHeaders noStore() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.setPragma("no-cache");
        return headers;
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, Object>> handleOAuth(OAuthException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getError());
        if (e.getMessage() != null) {
            body.put("error_description", e.getMessage());
        }
        return ResponseEntity.status(e.getStatus()).headers(noStore()).body(body);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooMany(TooManyRequestsException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "slow_down");
        body.put("error_description", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Map<String, Object>> handleBadBody(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "invalid_request");
        body.put("error_description", "Malformed request body");
        return ResponseEntity.badRequest().headers(noStore()).body(body);
    }
}
