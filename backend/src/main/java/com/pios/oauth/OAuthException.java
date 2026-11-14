package com.pios.oauth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** RFC 6749 style error ({@code error}, {@code error_description}). */
@Getter
public class OAuthException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description) {
        this(error, description, HttpStatus.BAD_REQUEST);
    }

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description);
    }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description);
    }

    public static OAuthException invalidClient(String description) {
        return new OAuthException("invalid_client", description, HttpStatus.UNAUTHORIZED);
    }
}
