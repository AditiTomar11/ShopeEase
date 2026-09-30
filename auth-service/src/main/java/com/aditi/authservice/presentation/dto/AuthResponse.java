package com.aditi.authservice.presentation.dto;

import com.aditi.authservice.domain.model.AuthenticatedUser;

/**
 * The JSON body returned by {@code POST /auth/login}.
 *
 * <p>{@code username} and {@code role} are echoed back on purpose. The browser
 * already decodes them out of the token (see {@code decodeToken.js} in the
 * frontend), but sending them explicitly means the UI never has to parse a JWT
 * just to render a name, and it keeps that decision reversible.
 *
 * <p>{@code tokenType: "Bearer"} is not decoration — it is the value the client
 * must echo in the {@code Authorization} header, and the standard
 * (RFC 6750) way to say so.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String username,
        String role) {

    public static AuthResponse from(AuthenticatedUser user, long expiresInMs) {
        return new AuthResponse(
                user.token(),
                "Bearer",
                expiresInMs,
                user.username(),
                user.role().name());
    }
}
