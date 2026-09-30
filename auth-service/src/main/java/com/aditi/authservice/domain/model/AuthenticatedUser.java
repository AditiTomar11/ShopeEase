package com.aditi.authservice.domain.model;

/**
 * The result of a successful authentication: who you are, plus the token that
 * proves it. Returned to the presentation layer, which decides how to serialise
 * it (here: a JSON {@code token} field).
 */
public record AuthenticatedUser(String username, Role role, String token) {
}
