package com.pios.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;

/**
 * Resolves the client IP. {@code X-Real-IP} / first {@code X-Forwarded-For} hop are trusted only
 * when the direct peer is on a private/loopback network (Caddy on the internal docker network);
 * otherwise the socket remote address is used.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /** Header set by the MCP server with the end client's IP; trusted only together with a valid internal secret. */
    public static final String MCP_CLIENT_IP_HEADER = "X-MCP-Client-IP";

    /**
     * Like {@link #resolve(HttpServletRequest)}, but when the request carries a valid internal secret
     * (i.e. it comes from the MCP server) the {@code X-MCP-Client-IP} header is preferred.
     */
    public static String resolve(HttpServletRequest request, boolean trustedInternal) {
        if (trustedInternal) {
            String mcpIp = clean(request.getHeader(MCP_CLIENT_IP_HEADER));
            if (mcpIp != null) {
                return mcpIp;
            }
        }
        return resolve(request);
    }

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote != null && isInternalPeer(remote)) {
            String realIp = clean(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null) {
                String first = clean(xff.split(",")[0]);
                if (first != null) {
                    return first;
                }
            }
        }
        return remote != null ? remote : "unknown";
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty() || v.length() > 45 || !v.matches("[0-9A-Fa-f:.\\[\\]]+")) {
            return null;
        }
        return v;
    }

    static boolean isInternalPeer(String remoteAddr) {
        // Only literal IPs are expected here; avoid DNS lookups.
        // Only IP literals (IPv4 dotted or IPv6 with ':'); anything else could trigger DNS resolution.
        boolean ipv4 = remoteAddr.matches("[0-9.]+");
        boolean ipv6 = remoteAddr.contains(":") && remoteAddr.matches("[0-9A-Fa-f:.\\[\\]]+");
        if (!ipv4 && !ipv6) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                return true;
            }
            byte[] b = addr.getAddress();
            // IPv6 unique local fc00::/7
            return b.length == 16 && (b[0] & 0xfe) == 0xfc;
        } catch (Exception e) {
            return false;
        }
    }
}
