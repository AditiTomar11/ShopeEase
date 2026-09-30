package com.aditi.authservice.domain.exception;

/**
 * Base type for every expected business failure.
 *
 * <p>Throwing a domain exception (instead of a bare {@code RuntimeException})
 * is what lets the presentation layer map failures to meaningful HTTP status
 * codes: a duplicate username becomes 409, bad credentials become 401 —
 * rather than every error surfacing as a 500.
 */
public abstract class AuthDomainException extends RuntimeException {

    private final String code;

    protected AuthDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable machine-readable code returned to the client in the error body. */
    public String getCode() {
        return code;
    }
}
