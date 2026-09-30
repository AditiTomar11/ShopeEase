package com.aditi.order_service.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One INFO line per request: method, path, status, duration, caller.
 *
 * <p>Run just after the correlation filter so every line it prints already
 * carries the id. The duration is measured around {@code chain.doFilter}, which
 * includes the controller, the service call, the database round trip and the
 * outbound Feign call — so it is the number that answers "is this endpoint
 * slow?", not just "how long did my method take?".
 *
 * <p>Request bodies and Authorization headers are never logged. Bodies can
 * contain passwords and card numbers; headers contain the token itself.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.request");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            String method = request.getMethod();
            String path = request.getRequestURI();
            int status = response.getStatus();
            String remote = request.getRemoteAddr();

            if (status >= 500) {
                log.error("{} {} -> {} in {}ms from {}", method, path, status, millis, remote);
            } else if (status >= 400) {
                log.warn("{} {} -> {} in {}ms from {}", method, path, status, millis, remote);
            } else {
                log.info("{} {} -> {} in {}ms from {}", method, path, status, millis, remote);
            }
        }
    }
}
