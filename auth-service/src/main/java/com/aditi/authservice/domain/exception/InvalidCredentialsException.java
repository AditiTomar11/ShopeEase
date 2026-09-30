package com.aditi.authservice.domain.exception;

/**
 * Thrown on a failed login.
 *
 * <p>The message is intentionally identical for "no such user" and "wrong
 * password". Distinguishing them would turn the login endpoint into a username
 * enumeration oracle.
 */
public class InvalidCredentialsException extends AuthDomainException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid username or password");
    }
}
