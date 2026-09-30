package com.aditi.api_gateway.filter;

import com.aditi.api_gateway.config.GatewaySecurityProperties;
import com.aditi.api_gateway.security.GatewayTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the edge security filter, using Spring's mock exchange.
 *
 * <p>These run with no network and no downstream service: the mock exchange is a
 * real {@code ServerWebExchange}, so the filter's routing decisions, status
 * codes and header mutation are all genuinely exercised. That is the value of
 * testing at this level rather than only unit-testing {@code parse()}.
 */
class JwtAuthGlobalFilterTest {

    private static final String SECRET = "a-test-secret-that-is-definitely-long-enough!!";
    private static final String ISSUER = "test-issuer";

    private JwtAuthGlobalFilter filter;
    private GatewaySecurityProperties rules;
    private boolean chainWasCalled;

    @BeforeEach
    void setUp() {
        rules = new GatewaySecurityProperties(
                List.of("/auth/login", "/auth/register", "/products"),
                List.of("GET", "HEAD", "OPTIONS"),
                List.of("/products/images/**", "/products/with-image"),
                List.of("/orders/**", "/products/**", "/auth/me"),
                false,
                true);
        filter = new JwtAuthGlobalFilter(new GatewayTokenService(SECRET, ISSUER), rules);
        chainWasCalled = false;
    }

    private String tokenFor(String username, String role, long ttlMillis) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuer(ISSUER)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    private ServerWebExchange exchange(String method, String path, String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(HttpMethod.valueOf(method), path);
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private ServerWebExchange run(String method, String path, String authHeader) {
        ServerWebExchange ex = exchange(method, path, authHeader);
        filter.filter(ex, e -> {
            chainWasCalled = true;
            return Mono.empty();
        }).block();
        return ex;
    }

    @Test
    @DisplayName("GET /products is public and passes through with no token")
    void publicGetNeedsNoToken() {
        ServerWebExchange ex = run("GET", "/products", null);

        assertTrue(chainWasCalled);
        assertNull(ex.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("POST /products is NOT public and is rejected with 401")
    void publicPathWithWriteMethodStillNeedsAToken() {
        // The subtle case: the path is in the public list, but only for GET.
        ServerWebExchange ex = run("POST", "/products", null);

        assertFalse(chainWasCalled);
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("POST /auth/login is public — otherwise nobody could ever log in")
    void loginIsPublic() {
        run("POST", "/auth/login", null);

        assertTrue(chainWasCalled);
    }

    @Test
    @DisplayName("a request with no token to a protected path gets 401")
    void missingTokenIs401() {
        ServerWebExchange ex = run("GET", "/orders", null);

        assertFalse(chainWasCalled);
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("a valid CUSTOMER token is accepted on /orders and identity headers are added")
    void validCustomerTokenPasses() {
        ServerWebExchange ex = run("GET", "/orders", "Bearer " + tokenFor("aditi", "CUSTOMER", 60_000));

        assertTrue(chainWasCalled);
        assertEquals("aditi", ex.getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals("CUSTOMER", ex.getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    @DisplayName("a CUSTOMER token is refused on an admin path with 403, not 401")
    void customerCannotReachAdminPath() {
        ServerWebExchange ex = run("POST", "/products/images", "Bearer " + tokenFor("aditi", "CUSTOMER", 60_000));

        assertFalse(chainWasCalled);
        // 403, not 401: we DO know who they are, they just may not do this.
        assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("an ADMIN token is allowed on an admin path")
    void adminMayUpload() {
        run("POST", "/products/images", "Bearer " + tokenFor("boss", "ADMIN", 60_000));

        assertTrue(chainWasCalled);
    }

    @Test
    @DisplayName("an expired token is rejected with 401")
    void expiredTokenIs401() {
        ServerWebExchange ex = run("GET", "/orders", "Bearer " + tokenFor("aditi", "CUSTOMER", -10_000));

        assertFalse(chainWasCalled);
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void foreignTokenIs401() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-totally-different-secret-of-sufficient-length!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("attacker")
                .issuer(ISSUER)
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        ServerWebExchange ex = run("POST", "/products/images", "Bearer " + forged);

        assertFalse(chainWasCalled);
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("a CORS preflight is never rejected — browsers do not send tokens on OPTIONS")
    void preflightIsAllowedThrough() {
        // This is the bug that makes "it works in Postman but fails in the browser":
        // the browser asks permission WITHOUT a token, so a filter that demands
        // one blocks every cross-origin call.
        MockServerHttpRequest request = MockServerHttpRequest
                .options("/orders")
                .header("Origin", "https://shope-ease-iota.vercel.app")
                .header("Access-Control-Request-Method", "POST")
                .build();

        filter.filter(MockServerWebExchange.from(request), e -> {
            chainWasCalled = true;
            return Mono.empty();
        }).block();

        assertTrue(chainWasCalled);
    }

    @Test
    @DisplayName("a client-supplied X-User-Role header is stripped, not trusted")
    void doesNotTrustClientSuppliedIdentityHeaders() {
        // Header spoofing attempt: the client claims to be an admin.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("aditi", "CUSTOMER", 60_000))
                .header("X-User-Name", "boss")
                .header("X-User-Role", "ADMIN")
                .build();

        ServerWebExchange ex = MockServerWebExchange.from(request);
        filter.filter(ex, e -> {
            chainWasCalled = true;
            return Mono.empty();
        }).block();

        // The gateway's own verified values replace the client's.
        assertNotNull(ex.getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals("aditi", ex.getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals("CUSTOMER", ex.getRequest().getHeaders().getFirst("X-User-Role"));
    }
}
