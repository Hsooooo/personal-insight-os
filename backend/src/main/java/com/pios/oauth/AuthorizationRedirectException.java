package com.pios.oauth;

import lombok.Getter;

/** /authorize error that must be reported to the client via redirect_uri (client + redirect_uri are valid). */
@Getter
public class AuthorizationRedirectException extends RuntimeException {
    private final String redirectUrl;

    public AuthorizationRedirectException(String redirectUrl, String error) {
        super(error);
        this.redirectUrl = redirectUrl;
    }
}
