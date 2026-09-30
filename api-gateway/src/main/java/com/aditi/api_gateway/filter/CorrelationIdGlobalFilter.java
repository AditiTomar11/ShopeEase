package com.aditi.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Mints the correlation id for the whole system — <b>here, and only here</b>.
 *
 * <h2>Why the gateway is the right place to mint it</h2>
 * The gateway is the first thing every request touches, whether it came from the
 * browser, from a mobile app, or from another service. Minting here guarantees
 * exactly one id per inbound request, shared by all five services downstream. If
 * each service minted its own, a single user action would produce five ids and
 * the "follow one request across the system" property would be lost.
 *
 * <h2>The contract</h2>
 * <ol>
 *   <li>reuse an inbound {@code X-Correlation-Id} if the caller supplied a sane
 *       one — that is what makes a trace survive across system boundaries;</li>
 *   <li>otherwise generate a UUID;</li>
 *   <li>put it in the MDC so the gateway's own logs carry it;</li>
 *   <li>echo it on the response so a user can quote it in a bug report;</li>
 *   <li>forward it upstream so every downstream service logs the same id.</li>
 * </ol>
 *
 * <h2>Trust boundary</h2>
 * An inbound id is attacker-controlled, so it is length-checked and sanitised
 * before being written to a log line — otherwise a client could inject newlines
 * and forge log entries, or paste megabytes into every log line of five services.
 * That class of bug is a real log-injection vulnerability, not a theoretical one.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;
    private static final String SAFE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = sanitise(exchange.getRequest().getHeaders().getFirst(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build())
                // doFinally, not doOnSuccess: the id must be cleared on error and
                // on cancel too, and Reactor's context is per-subscription so this
                // is safe under concurrency.
                .doFinally(signal -> MDC.remove(MDC_KEY));
    }

    /**
     * Returns a safe id, or {@code null} when the caller's value must be rejected.
     *
     * <p>Only alphanumerics, hyphen and underscore survive. That single regex
     * removes the newlines a log-injection attack needs.
     */
    private static String sanitise(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (SAFE_CHARS.indexOf(candidate.charAt(i)) == -1) {
                log.debug("Discarding an unsafe inbound correlation id");
                return null;
            }
        }
        return candidate;
    }
}
