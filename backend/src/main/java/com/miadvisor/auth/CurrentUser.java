package com.miadvisor.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated user's id from the JWT subject. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
