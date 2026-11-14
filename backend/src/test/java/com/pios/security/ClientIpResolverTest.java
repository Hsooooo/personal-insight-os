package com.pios.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void trustsForwardedHeadersOnlyFromInternalPeer() {
        MockHttpServletRequest internal = new MockHttpServletRequest();
        internal.setRemoteAddr("172.18.0.5");
        internal.addHeader("X-Real-IP", "203.0.113.7");
        assertThat(ClientIpResolver.resolve(internal)).isEqualTo("203.0.113.7");

        MockHttpServletRequest xff = new MockHttpServletRequest();
        xff.setRemoteAddr("127.0.0.1");
        xff.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");
        assertThat(ClientIpResolver.resolve(xff)).isEqualTo("198.51.100.1");

        MockHttpServletRequest external = new MockHttpServletRequest();
        external.setRemoteAddr("198.51.100.9");
        external.addHeader("X-Real-IP", "1.1.1.1");
        assertThat(ClientIpResolver.resolve(external)).isEqualTo("198.51.100.9");
    }

    @Test
    void ignoresGarbageHeader() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.2");
        r.addHeader("X-Real-IP", "evil.example.com");
        assertThat(ClientIpResolver.resolve(r)).isEqualTo("10.0.0.2");
    }

    @Test
    void mcpClientIpHeaderOnlyTrustedWithInternalSecret() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("172.18.0.7");
        r.addHeader(ClientIpResolver.MCP_CLIENT_IP_HEADER, "203.0.113.50");
        assertThat(ClientIpResolver.resolve(r, true)).isEqualTo("203.0.113.50");
        assertThat(ClientIpResolver.resolve(r, false)).isEqualTo("172.18.0.7");
    }
}
