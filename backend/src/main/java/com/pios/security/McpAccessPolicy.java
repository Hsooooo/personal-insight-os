package com.pios.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.function.Supplier;

/**
 * Authorization for every non-public endpoint:
 * <ul>
 *   <li>unauthenticated → denied (entry point answers 401)</li>
 *   <li>normal web-login principal → allowed</li>
 *   <li>{@code ROLE_MCP} principal (OAuth access token / API key) → only the allowlist below,
 *       with the required scope; everything else → 403</li>
 * </ul>
 */
public class McpAccessPolicy implements AuthorizationManager<RequestAuthorizationContext> {

    private record Rule(RequestMatcher matcher, String requiredAuthority) {
    }

    private static final List<Rule> RULES = List.of(
            // mcp:read
            read(HttpMethod.GET, "/api/dashboard/summary"),
            read(HttpMethod.GET, "/api/activities"),
            read(HttpMethod.GET, "/api/activities/*"),
            read(HttpMethod.GET, "/api/activities/*/laps"),
            read(HttpMethod.GET, "/api/health/metrics"),
            read(HttpMethod.GET, "/api/health/sleep"),
            read(HttpMethod.GET, "/api/goals"),
            read(HttpMethod.GET, "/api/insights"),
            read(HttpMethod.GET, "/api/graph"),
            read(HttpMethod.GET, "/api/briefings/latest"),
            read(HttpMethod.POST, "/api/ask"),
            // mcp:write
            write(HttpMethod.POST, "/api/goals"),
            write(HttpMethod.PATCH, "/api/goals/*"),
            write(HttpMethod.POST, "/api/insights/*/save"),
            write(HttpMethod.POST, "/api/insights/*/feedback"),
            write(HttpMethod.POST, "/api/data-sources/garmin/sync"),
            write(HttpMethod.POST, "/api/briefings/generate")
    );

    private static Rule read(HttpMethod method, String pattern) {
        return new Rule(AntPathRequestMatcher.antMatcher(method, pattern), PiosAuthorities.SCOPE_MCP_READ);
    }

    private static Rule write(HttpMethod method, String pattern) {
        return new Rule(AntPathRequestMatcher.antMatcher(method, pattern), PiosAuthorities.SCOPE_MCP_WRITE);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(isAllowed(authentication.get(), context.getRequest()));
    }

    public boolean isAllowed(Authentication auth, HttpServletRequest request) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        if (!PiosAuthorities.has(auth, PiosAuthorities.ROLE_MCP)) {
            return true;
        }
        for (Rule rule : RULES) {
            if (rule.matcher().matches(request)) {
                return PiosAuthorities.has(auth, rule.requiredAuthority());
            }
        }
        return false;
    }
}
