package com.pios.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Random token generation, hashing and PKCE helpers. */
public final class OAuthTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** RFC 7636 §4.1: 43–128 chars of [A-Z a-z 0-9 - . _ ~] */
    private static final Pattern VERIFIER = Pattern.compile("^[A-Za-z0-9\\-._~]{43,128}$");
    /** S256 challenge = base64url(sha256) without padding = 43 chars */
    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9\\-_]{43}$");

    private OAuthTokens() {
    }

    public static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isValidCodeChallenge(String challenge) {
        return challenge != null && CHALLENGE.matcher(challenge).matches();
    }

    public static String s256Challenge(String verifier) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Constant-time PKCE S256 verification. */
    public static boolean verifyPkce(String verifier, String challenge) {
        if (verifier == null || challenge == null || !VERIFIER.matcher(verifier).matches()) {
            return false;
        }
        return constantTimeEquals(s256Challenge(verifier), challenge);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
