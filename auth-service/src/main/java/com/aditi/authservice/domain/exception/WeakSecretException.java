package com.aditi.authservice.domain.exception;

/**
 * Thrown at start-up when {@code jwt.secret} is too short to sign with HS256.
 *
 * <p>Failing loudly at boot is deliberate: a service that boots with a weak
 * key and only fails on the first login is far harder to debug than one that
 * refuses to start with a clear message.
 */
public class WeakSecretException extends AuthDomainException {

    public WeakSecretException(int actualBytes, int requiredBytes) {
        super("WEAK_JWT_SECRET",
                "jwt.secret is " + actualBytes + " bytes but HS256 requires at least "
                        + requiredBytes + " bytes (256 bits). Set JWT_SECRET to a longer random value.");
    }
}
