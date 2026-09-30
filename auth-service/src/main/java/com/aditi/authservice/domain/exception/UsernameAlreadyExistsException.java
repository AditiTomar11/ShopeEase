package com.aditi.authservice.domain.exception;

/** Thrown when someone registers a username that is already taken. */
public class UsernameAlreadyExistsException extends AuthDomainException {

    public UsernameAlreadyExistsException(String username) {
        super("USERNAME_TAKEN", "Username '" + username + "' is already registered");
    }
}
