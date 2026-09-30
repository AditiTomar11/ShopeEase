package com.aditi.authservice.domain.port;

import com.aditi.authservice.domain.model.User;

import java.util.Optional;

/**
 * Outbound port for everything token-shaped (today: JWT).
 *
 * <p>The application service says "issue a token for this user" and "tell me
 * who this token belongs to". It never imports JJWT, never touches
 * {@code SignatureAlgorithm}, and never signs anything itself.
 *
 * <p>That is what lets the same {@code AuthService} be unit-tested with a
 * three-line fake, and what would let us move from self-signed JWTs to an
 * external IdP (OIDC) by replacing only this port's implementation.
 */
public interface TokenService {

    /** Issues a signed access token carrying at least username + role. */
    String issue(User user);

    /**
     * Verifies the signature and expiry, then returns the claims.
     * An invalid, tampered or expired token yields {@link Optional#empty()} —
     * never an exception, because "this token is bad" is a normal outcome.
     */
    Optional<TokenClaims> parse(String token);

    /** Convenience for callers that only need a yes/no answer. */
    default boolean isValid(String token) {
        return parse(token).isPresent();
    }

    /** Token lifetime in milliseconds, surfaced in the login response. */
    long getExpiresInMillis();

    /**
     * Verified contents of a token. Only ever produced by
     * {@link #parse(String)}, i.e. after the signature has been checked.
     */
    record TokenClaims(String username, String role) {
    }
}
