package com.aditi.authservice.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a {@code correlationId} and puts it in the SLF4J MDC.
 *
 * <h2>The problem this solves</h2>
 * In a five-service system a single user action becomes five HTTP calls, each
 * logged to a different place. Asking "what happened to order #42?" across five
 * log streams is painful without a shared key. A correlation id is that key:
 * the <b>gateway mints it once</b> and forwards it as {@code X-Correlation-Id};
 * every downstream service copies the incoming header (or mints one) into the
 * MDC, so its logback pattern prints it on every line.
 *
 * <p>Then one grep — {@code 7f3c1a2e-…} — reconstructs the whole call chain
 * across services.
 *
 * <h2>Why the MDC</h2>
 * MDC (Mapped Diagnostic Context) is a thread-local map that logging frameworks
 * expose to the pattern as {@code %X{correlationId}}. It lets you add context to
 * every log line without threading a parameter through every method signature.
 *
 * <p>MDC is thread-local, which is precisely why it must be cleared in a
 * {@code finally} block: servlet containers reuse threads, so a leaked value
 * would silently label an unrelated later request.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Reject absurd values coming from outside rather than logging them. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming == null || incoming.isBlank() || incoming.length() > MAX_LENGTH)
                ? UUID.randomUUID().toString()
                : incoming.trim();

        MDC.put(MDC_KEY, correlationId);
        // Echo it back so a browser / curl user can quote it in a bug report.
        response.setHeader(HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
