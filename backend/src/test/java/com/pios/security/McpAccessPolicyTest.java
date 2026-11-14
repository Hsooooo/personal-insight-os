package com.pios.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

class McpAccessPolicyTest {

    private final McpAccessPolicy policy = new McpAccessPolicy();

    private static Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken(1L, null, AuthorityUtils.createAuthorityList(authorities));
    }

    private static MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setServletPath(path);
        return r;
    }

    private static final Authentication USER = auth(PiosAuthorities.ROLE_USER);
    private static final Authentication MCP_READ = auth(PiosAuthorities.ROLE_MCP, PiosAuthorities.SCOPE_MCP_READ);
    private static final Authentication MCP_RW = auth(PiosAuthorities.ROLE_MCP, PiosAuthorities.SCOPE_MCP_READ,
            PiosAuthorities.SCOPE_MCP_WRITE);

    @Test
    void anonymousIsDenied() {
        assertThat(policy.isAllowed(null, req("GET", "/api/goals"))).isFalse();
        assertThat(policy.isAllowed(new AnonymousAuthenticationToken("k", "anon",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")), req("GET", "/api/goals"))).isFalse();
    }

    @Test
    void normalUserCanCallEverything() {
        assertThat(policy.isAllowed(USER, req("GET", "/api/settings/llm-providers"))).isTrue();
        assertThat(policy.isAllowed(USER, req("DELETE", "/api/auth/api-keys/1"))).isTrue();
    }

    @Test
    void mcpIsBlockedOutsideAllowlist() {
        assertThat(policy.isAllowed(MCP_RW, req("GET", "/api/settings/llm-providers"))).isFalse();
        assertThat(policy.isAllowed(MCP_RW, req("POST", "/api/auth/api-keys"))).isFalse();
        assertThat(policy.isAllowed(MCP_RW, req("GET", "/api/oauth/grants"))).isFalse();
        assertThat(policy.isAllowed(MCP_RW, req("DELETE", "/api/goals/3"))).isFalse();
        assertThat(policy.isAllowed(MCP_RW, req("GET", "/api/finance/transactions"))).isFalse();
        assertThat(policy.isAllowed(MCP_RW, req("POST", "/api/admin/backfill"))).isFalse();
    }

    @Test
    void mcpReadAllowlist() {
        assertThat(policy.isAllowed(MCP_READ, req("GET", "/api/goals"))).isTrue();
        assertThat(policy.isAllowed(MCP_READ, req("GET", "/api/activities/12/laps"))).isTrue();
        assertThat(policy.isAllowed(MCP_READ, req("GET", "/api/activities/12"))).isTrue();
        assertThat(policy.isAllowed(MCP_READ, req("POST", "/api/ask"))).isTrue();
        assertThat(policy.isAllowed(MCP_READ, req("GET", "/api/dashboard/summary"))).isTrue();
    }

    @Test
    void writeEndpointsNeedWriteScope() {
        assertThat(policy.isAllowed(MCP_READ, req("POST", "/api/goals"))).isFalse();
        assertThat(policy.isAllowed(MCP_READ, req("PATCH", "/api/goals/5"))).isFalse();
        assertThat(policy.isAllowed(MCP_READ, req("POST", "/api/data-sources/garmin/sync"))).isFalse();
        assertThat(policy.isAllowed(MCP_RW, req("POST", "/api/goals"))).isTrue();
        assertThat(policy.isAllowed(MCP_RW, req("POST", "/api/insights/9/feedback"))).isTrue();
        assertThat(policy.isAllowed(MCP_RW, req("POST", "/api/briefings/generate"))).isTrue();
    }

    @Test
    void readEndpointsNeedReadScope() {
        Authentication writeOnly = auth(PiosAuthorities.ROLE_MCP, PiosAuthorities.SCOPE_MCP_WRITE);
        assertThat(policy.isAllowed(writeOnly, req("GET", "/api/goals"))).isFalse();
    }
}
