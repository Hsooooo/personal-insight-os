package com.pios.oauth;

/** Consent request / grant not found (or expired) → 404 ApiResponse. */
public class OAuthNotFoundException extends RuntimeException {
    public OAuthNotFoundException(String message) {
        super(message);
    }
}
