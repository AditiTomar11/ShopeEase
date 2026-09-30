package com.aditi.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * One INFO line per request at the edge, with status and total duration.
 *
 * <p>Because this wraps the routing filter, the measured time is the
 * <em>end-to-end</em> time including the downstream service and its database
 * round trip — which is the number you actually need when a user says "the site
 * is slow". Timing only a handler function would hide the network entirely.
 *
 * <p>Reactor note: this is a reactive chain, so there is no thread to block and
 * no thread-local to worry about beyond the MDC, which
 * {@link CorrelationIdGlobalFilter} already scopes with {@code doFinally}.
 * Measuring with {@link System#nanoTime()} is safe here because it is a cheap
 * monotonic clock read, not I/O.
 */
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("http.request");

    /** The liveness probe runs every few seconds; logging it is pure noise. */
    private static final String[] QUIET_PATHS = {"/health", "/actuator/health"};

    @Override
    public int getOrder() {
        // Late enough to wrap routing, early enough to still be before the
        // response is committed to the client.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethodValue();
        String path = request.getPath().value();

        return chain.filter(exchange).doFinally(signal -> {
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            ServerHttpResponse response = exchange.getResponse();
            Integer status = response.getStatusCode() == null ? null : response.getStatusCode().value();

            if (isQuiet(path)) {
                return;
            }
            if (status != null && status >= 500) {
                log.error("{} {} -> {} in {}ms", method, path, status, millis);
            } else if (status != null && status >= 400) {
                log.warn("{} {} -> {} in {}ms", method, path, status, millis);
            } else {
                log.info("{} {} -> {} in {}ms", method, path, status == null ? "-" : status, millis);
            }
        });
    }

    private static boolean isQuiet(String path) {
        for (String quiet : QUIET_PATHS) {
            if (path.equals(quiet)) {
                return true;
            }
        }
        return false;
    }
}
