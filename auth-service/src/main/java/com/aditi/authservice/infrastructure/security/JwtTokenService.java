package com.aditi.authservice.infrastructure.security;

import com.aditi.authservice.domain.exception.WeakSecretException;
import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.port.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * JJWT implementation of the domain's {@link TokenService} port.
 *
 * <h2>What a JWT actually is</h2>
 * Three Base64URL segments separated by dots:
 * <pre>
 *   header.payload.signature
 *   {"alg":"HS256","typ":"JWT"} . {"sub":"aditi","role":"ADMIN","iat":…,"exp":…} . hmac-sha256(secret, header + "." + payload)
 * </pre>
 * The payload is only <em>encoded</em>, never encrypted — anyone holding the
 * token can read it. That is why nothing secret may ever go in there.
 *
 * <h2>How verification works</h2>
 * The server recomputes the signature over the first two segments using its own
 * copy of the secret and compares. If the payload was tampered with, the
 * recomputed signature no longer matches, so verification fails. Combined with
 * the {@code exp} claim this makes the token both tamper-evident and expiring,
 * with <b>no server-side session to look up</b> — which is exactly what makes
 * the token usable across all five services without shared state.
 *
 * <p>{@code HS256} is a symmetric scheme: the same secret signs and verifies, so
 * every service that must validate a token needs the shared {@code JWT_SECRET}.
 * The asymmetric alternative is {@code RS256} (private key signs, public key
 * verifies), which lets third parties verify without being able to mint tokens.
 */
public class JwtTokenService implements TokenService {

    /**
     * HS256 needs at least 256 bits of key material. JJWT enforces this and
     * throws {@code WeakKeyException}; we check it up front with a message an
     * operator can act on.
     */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey key;
    private final long expirationMillis;
    private final String issuer;

    public JwtTokenService(String secret, long expirationMillis, String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            int actual = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
            throw new WeakSecretException(actual, MIN_SECRET_BYTES);
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
        this.issuer = issuer;
    }

    @Override
    public String issue(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    @Override
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
            String role = claims.get(CLAIM_ROLE, String.class);
            if (username == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(username, role));
        } catch (JwtException | IllegalArgumentException invalid) {
            // Covers malformed input, bad signature, expired token and
            // unsupported algorithm. All of them mean the same thing to a
            // caller: "this token is not acceptable".
            return Optional.empty();
        }
    }

    @Override
    public long getExpiresInMillis() {
        return expirationMillis;
    }
}
