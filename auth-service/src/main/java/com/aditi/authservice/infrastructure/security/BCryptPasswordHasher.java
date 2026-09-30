package com.aditi.authservice.infrastructure.security;

import com.aditi.authservice.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt implementation of the domain's {@link PasswordHasher} port.
 *
 * <p>Why BCrypt: it is deliberately slow and salted per-hash, so a stolen
 * table of hashes cannot be attacked with a fast GPU-friendly algorithm like
 * plain SHA-256. The cost factor is 10 by default, i.e. roughly 50–100 ms per
 * verification on typical hardware — slow for an attacker, invisible to a user.
 *
 * <p>Note this class is framework-free apart from the one Spring Security type
 * it delegates to, and it is created explicitly in {@code BeanModule} rather
 * than being component-scanned.
 */
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this(10);
    }

    public BCryptPasswordHasher(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException malformed) {
            // storedHash is not a BCrypt string (e.g. a legacy plaintext row).
            // Treat it as a failed login instead of a 500.
            return false;
        }
    }
}
