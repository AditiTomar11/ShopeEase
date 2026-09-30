package com.aditi.authservice.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the BCrypt adapter behind the domain's {@code PasswordHasher} port. */
class BCryptPasswordHasherTest {

    /** Low cost factor so the suite stays fast; production uses the default 10. */
    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(4);

    @Test
    @DisplayName("verifies the correct password and rejects a near miss")
    void verifiesCorrectPassword() {
        String hash = hasher.hash("secret123");

        assertTrue(hasher.matches("secret123", hash));
        assertFalse(hasher.matches("secret124", hash));
    }

    @Test
    @DisplayName("never stores the plaintext")
    void doesNotStorePlaintext() {
        assertFalse(hasher.hash("secret123").contains("secret123"));
    }

    @Test
    @DisplayName("produces a modular-crypt hash, not a bare digest")
    void producesModularCryptFormat() {
        // "$2a$10$..." — version, cost factor and salt are all encoded in the
        // hash itself, so the cost can be raised later without invalidating
        // existing passwords (re-hash on next successful login).
        String hash = hasher.hash("secret123");

        assertTrue(hash.startsWith("$2"), "expected a BCrypt modular-crypt hash but got: " + hash);
    }

    @Test
    @DisplayName("salts, so the same password hashes differently every time")
    void saltsEachHash() {
        // Without a salt, two users sharing a password would share a hash, and
        // one precomputed table would crack both at once.
        assertNotEquals(hasher.hash("secret123"), hasher.hash("secret123"));
    }

    @Test
    @DisplayName("treats a malformed stored hash as a failed login, not a 500")
    void survivesMalformedHash() {
        // Guards against a legacy plaintext row taking the whole endpoint down.
        assertFalse(hasher.matches("secret123", "not-a-bcrypt-hash"));
        assertFalse(hasher.matches(null, "x"));
        assertFalse(hasher.matches("secret123", null));
    }
}
