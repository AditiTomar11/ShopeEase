package com.aditi.authservice.infrastructure.security;

import com.aditi.authservice.domain.exception.WeakSecretException;
import com.aditi.authservice.domain.model.Role;
import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.port.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the JJWT adapter.
 *
 * <p>JJWT is a pure-CPU library, so testing it needs no container and no
 * network — which is exactly why this class can prove the security properties
 * that matter: a token signed by somebody else is rejected, and a modified
 * payload is rejected.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "a-test-secret-that-is-definitely-long-enough!!";
    private static final String ISSUER = "test-issuer";

    private JwtTokenService service() {
        return new JwtTokenService(SECRET, 60_000L, ISSUER);
    }

    private static User user() {
        return User.newUser("aditi", "hashed", Role.ADMIN);
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("issues a three-segment token")
    void issuesCompactToken() {
        assertEquals(3, service().issue(user()).split("\\.").length);
    }

    @Test
    @DisplayName("round-trips username and role")
    void roundTripsClaims() {
        TokenService.TokenClaims claims = service().parse(service().issue(user())).orElseThrow();

        assertEquals("aditi", claims.username());
        assertEquals("ADMIN", claims.role());
    }

    @Test
    @DisplayName("exposes the payload as plain Base64 — JWTs are signed, not encrypted")
    void payloadIsReadable() {
        String payloadSegment = service().issue(user()).split("\\.")[1];
        String decoded = new String(Base64.getUrlDecoder().decode(payloadSegment), StandardCharsets.UTF_8);

        // Anyone holding the token can read this. That is precisely why a
        // password must never be placed in a JWT payload.
        assertTrue(decoded.contains("aditi"));
        assertTrue(decoded.contains("ADMIN"));
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSignature() {
        String foreign = new JwtTokenService("a-completely-different-secret-of-length-32!!", 60_000L, ISSUER)
                .issue(user());

        assertFalse(service().isValid(foreign));
    }

    @Test
    @DisplayName("rejects a tampered payload")
    void rejectsTamperedPayload() {
        String[] parts = service().issue(user()).split("\\.");
        String forgedPayload = base64Url(
                "{\"sub\":\"attacker\",\"role\":\"ADMIN\",\"iss\":\"" + ISSUER + "\"}");

        assertFalse(service().isValid(parts[0] + "." + forgedPayload + "." + parts[2]));
    }

    @Test
    @DisplayName("rejects an already-expired token")
    void rejectsExpired() {
        String expired = new JwtTokenService(SECRET, -1_000L, ISSUER).issue(user());

        assertFalse(service().isValid(expired));
    }

    @Test
    @DisplayName("rejects a token minted by a different issuer")
    void rejectsWrongIssuer() {
        String otherIssuer = new JwtTokenService(SECRET, 60_000L, "somebody-else").issue(user());

        assertFalse(service().isValid(otherIssuer));
    }

    @Test
    @DisplayName("returns empty for garbage instead of throwing")
    void returnsEmptyForGarbage() {
        JwtTokenService service = service();

        assertEquals(Optional.empty(), service.parse("not-a-token"));
        assertEquals(Optional.empty(), service.parse(""));
        assertEquals(Optional.empty(), service.parse(null));
    }

    @Test
    @DisplayName("refuses to start with a secret shorter than 256 bits")
    void rejectsWeakSecret() {
        // Fails at construction — i.e. at application start-up — rather than on
        // the first login request, which is far easier to diagnose in production.
        assertThrows(WeakSecretException.class, () -> new JwtTokenService("too-short", 1000L, ISSUER));
    }
}
