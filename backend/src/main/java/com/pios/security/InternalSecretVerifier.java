package com.pios.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Verifies the backend ↔ MCP shared secret header in constant time. */
@Component
public class InternalSecretVerifier {

    public static final String HEADER = "X-PIOS-Internal-Secret";
    private static final int MIN_LENGTH = 32;

    private final byte[] expected;

    public InternalSecretVerifier(@Value("${pios.mcp-internal-secret}") String secret) {
        this.expected = secret == null ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostConstruct
    void validate() {
        if (expected == null || expected.length < MIN_LENGTH) {
            throw new IllegalStateException(
                    "pios.mcp-internal-secret must be at least " + MIN_LENGTH + " characters (set PIOS_MCP_INTERNAL_SECRET)");
        }
    }

    public boolean matches(String provided) {
        if (provided == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
    }

    public boolean matches(HttpServletRequest request) {
        return matches(request.getHeader(HEADER));
    }
}
