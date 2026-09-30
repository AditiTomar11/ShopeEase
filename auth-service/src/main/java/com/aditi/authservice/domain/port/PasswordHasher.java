package com.aditi.authservice.domain.port;

/**
 * Outbound port (hexagonal "driven port").
 *
 * <p>The domain needs to hash and verify passwords, but it must not know that
 * BCrypt — or Spring Security — exists. It declares <em>what</em> it needs; the
 * infrastructure layer supplies <em>how</em>. That is dependency inversion: the
 * arrow points inwards.
 *
 * <p>Swapping BCrypt for Argon2id, or for a remote secrets service, is a
 * one-class change with zero edits to {@code domain} or {@code application}.
 */
public interface PasswordHasher {

    /** @return a salted one-way hash that {@link #matches} can verify. */
    String hash(String rawPassword);

    /**
     * Constant-time comparison performed by the hashing algorithm itself.
     * A failed parse (e.g. a plaintext value left over from an old schema)
     * returns false rather than throwing.
     */
    boolean matches(String rawPassword, String storedHash);
}
