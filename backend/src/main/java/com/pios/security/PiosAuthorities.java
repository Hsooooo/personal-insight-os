package com.pios.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Authority names used by {@link JwtAuthenticationFilter} and {@link McpAccessPolicy}. */
public final class PiosAuthorities {

    public static final String ROLE_USER = "ROLE_USER";
    /** Marker for principals authenticated via OAuth access token or pios_ API key (MCP). */
    public static final String ROLE_MCP = "ROLE_MCP";
    public static final String SCOPE_MCP_READ = "SCOPE_mcp:read";
    public static final String SCOPE_MCP_WRITE = "SCOPE_mcp:write";

    private PiosAuthorities() {
    }

    public static boolean has(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
