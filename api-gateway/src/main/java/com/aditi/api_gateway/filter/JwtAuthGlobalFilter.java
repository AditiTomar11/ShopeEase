package com.aditi.api_gateway.filter;

import com.aditi.api_gateway.config.GatewaySecurityProperties;
import com.aditi.api_gateway.security.GatewayTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * The single security checkpoint for the whole system.
 *
 * <h2>What a "global filter" is</h2>
 * Spring Cloud Gateway is built on Spring's {@code WebFilter} chain. A
 * {@code GlobalFilter} runs for <em>every</em> request regardless of which route
 * it matched, in {@link Ordered} sequence, whereas a route-level filter declared
 * inside {@code application.yml} runs only for its own route. Security and
 * correlation-id belong to all routes, so they are global.
 *
 * <h2>Order, and why it is what it is</h2>
 * <pre>
 *   -100  CorrelationIdFilter       mint/forward the id, so every later log has it
 *    -50  RemoveCachedBodyFilter    (not used here)
 *      0  JwtAuthGlobalFilter       reject early, before any downstream connection
 *   +100  NettyRoutingFilter        actually make the upstream call
 * </pre>
 * Running authorisation <em>before</em> routing matters: an unauthenticated
 * request should never occupy an upstream connection, a thread, or a slot in the
 * downstream connection pool.
 *
 * <h2>Why 401 and not 403 for a bad token</h2>
 * 401 means "I do not know who you are — authenticate and retry"; 403 means "I
 * know exactly who you are and you still may not do this". Conflating them makes
 * client retry logic wrong: a client that retries a 403 in a loop will never
 * succeed, whereas retrying after refreshing a token can.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final GatewayTokenService tokenService;
    private final GatewaySecurityProperties rules;

    public JwtAuthGlobalFilter(GatewayTokenService tokenService, GatewaySecurityProperties rules) {
        this.tokenService = tokenService;
        this.rules = rules;
    }

    @Override
    public int getOrder() {
        // Ahead of NettyRoutingFilter, behind the correlation-id filter.
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethodValue();

        // A CORS preflight carries no Authorization header by design — the
        // browser sends it precisely to find out whether it MAY send one.
        // Rejecting it would break every cross-origin call from the browser
        // while leaving curl and Postman working, which is a miserable bug to
        // chase.
        if (CorsUtils.isPreFlightRequest(request)) {
            return chain.filter(exchange);
        }

        // Rule 1: public path + public method -> straight through.
        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        // Rule 2: public path but a non-public method (e.g. POST /products) ->
        //         it is not public after all, so fall through to the checks below.
        boolean needsAuth = requiresAuthentication(path);
        if (!needsAuth) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request);
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "MISSING_TOKEN",
                    "Authorization header with a Bearer token is required");
        }

        var claims = tokenService.parse(token);
        if (claims.isEmpty()) {
            // Expired, tampered with, or signed with a different secret. All three
            // are the same thing to the client: get a new token.
            log.warn("Rejected request to {} {} — invalid or expired token (corr={})",
                    method, path, exchange.getRequest().getHeaders().getFirst("X-Correlation-Id"));
            return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Token is invalid or has expired");
        }

        String role = claims.get().role();

        // Rule 3: admin-only paths.
        if (matchesAny(rules.adminPaths(), path) && !"ADMIN".equals(role)) {
            log.warn("Rejected {} {} — role {} is not ADMIN (corr={})",
                    method, path, role, exchange.getRequest().getHeaders().getFirst("X-Correlation-Id"));
            return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "This operation requires the ADMIN role");
        }

        log.debug("Authorised {} {} as username={} role={}",
                method, path, claims.get().username(), role);

        return chain.filter(forwardIdentity(exchange, claims.get().username(), role));
    }

    /**
     * Forwards the verified identity downstream and optionally strips the raw
     * token.
     *
     * <p>{@code X-User-Name} / {@code X-User-Role} are <b>convenience</b>
     * headers: they save a downstream service from re-verifying the token. They
     * are only trustworthy because the gateway is the only thing that can set
     * them — which is exactly why the services must not be reachable from
     * outside. They are stripped from the <em>inbound</em> request first, so a
     * client cannot pre-set them and have them survive.
     */
    private ServerWebExchange forwardIdentity(ServerWebExchange exchange, String username, String role) {
        ServerHttpRequest.Builder mutate = exchange.getRequest().mutate();

        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (headers.getFirst("X-User-Name") != null || headers.getFirst("X-User-Role") != null) {
            mutate.headers(h -> {
                h.remove("X-User-Name");
                h.remove("X-User-Role");
            });
        }
        if (rules.forwardIdentityHeaders()) {
            mutate.header("X-User-Name", username);
            mutate.header("X-User-Role", role);
        }
        if (rules.stripAuthorization()) {
            mutate.headers(h -> h.remove(HttpHeaders.AUTHORIZATION));
        }

        return exchange.mutate().request(mutate.build()).build();
    }

    private boolean isPublic(String path, String method) {
        if (!matchesAny(rules.publicPaths(), path)) {
            return false;
        }
        for (String allowed : rules.publicMethods()) {
            if (allowed.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresAuthentication(String path) {
        return matchesAny(rules.authenticatedPaths(), path) || matchesAny(rules.adminPaths(), path);
    }

    private static boolean matchesAny(java.util.List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Writes a JSON error body and completes the response.
     *
     * <p>Returning JSON rather than an empty body matters: the React
     * interceptor on the frontend reads {@code error.code} to decide whether to
     * log out, and a bare 401 with no body gives it nothing to work with.
     */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status,
                              String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\""
                + ",\"code\":\"" + code + "\""
                + ",\"message\":\"" + message + "\""
                + ",\"path\":\"" + exchange.getRequest().getPath().value() + "\"}";

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
