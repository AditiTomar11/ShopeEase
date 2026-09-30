package com.aditi.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Verifies JWTs at the gateway.
 *
 * <h2>Why the gateway verifies and the services trust it</h2>
 * Verifying in one place means:
 * <ul>
 *   <li>one implementation of the rules instead of five, so they cannot drift;</li>
 *   <li>a rejected request is rejected <em>before</em> it consumes a connection to
 *       a business service, a database connection, or a thread;</li>
 *   <li>and a service never has to hold the signing secret at all — only the
 *       gateway does.</li>
 * </ul>
 * The trade-off is that a service is now only as protected as the network around
 * it: anything that can reach product-service directly bypasses the gateway
 * entirely. Two ways to close that, both worth naming in an interview:
 * <ul>
 *   <li>keep services on a private network (Render private services, a Kubernetes
 *       NetworkPolicy, a VPC security group) so only the gateway can connect;</li>
 *   <li>or run the same verification filter inside each service, accepting the
 *       duplicated configuration in exchange for defence in depth.</li>
 * </ul>
 * This project does the first in deployment (documented in the README) and keeps
 * the {@code Authorization} header on the forwarded request so the second remains
 * a small, local change.
 *
 * <h2>Why this is safe on a reactive event loop</h2>
 * Spring Cloud Gateway is WebFlux, so a filter runs on a Netty event-loop thread
 * and must not block. HMAC-SHA256 verification is pure CPU over a few hundred
 * bytes — microseconds — so it is fine here, and this is the same reasoning that
 * makes a WebFlux application avoid {@code Thread.sleep}, JDBC and blocking file
 * I/O. Swapping HS256 for RS256 with a remote JWKS endpoint would change the
 * answer, because fetching the key is network I/O and would have to be offloaded
 * with {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}.
 */
@Component
public class GatewayTokenService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final String issuer;

    public GatewayTokenService(@Value("${jwt.secret}") String secret,
                               @Value("${jwt.issuer:shopease-auth-service}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256. Set JWT_SECRET on the gateway.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    public Optional<TokenClaims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            if (username == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(username, role));
        } catch (JwtException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public record TokenClaims(String username, String role) {
    }
}
