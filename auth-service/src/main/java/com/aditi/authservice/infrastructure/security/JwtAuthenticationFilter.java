package com.aditi.authservice.infrastructure.security;

import com.aditi.authservice.domain.port.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns a {@code Authorization: Bearer <jwt>} header into an authenticated
 * {@code SecurityContext}.
 *
 * <p>Why a servlet filter and not a {@code @PreAuthorize} on each method?
 * Because authorisation is cross-cutting. A filter runs once, ahead of every
 * controller, and decides "is anyone authenticated at all?" so that individual
 * methods only have to express the finer question "…and are they an admin?".
 *
 * <p>The filter is deliberately forgiving: a missing or invalid token does not
 * throw here. It simply leaves the context empty, and the authorisation rules
 * in {@code SecurityConfig} then answer 401/403. Letting the filter write the
 * error body would also make it impossible to distinguish "anonymous" from
 * "authenticated but not allowed".
 *
 * <p>Stateless by design: nothing is written to the session, so the same filter
 * would be safe to run in every service.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenService.parse(token).ifPresent(claims -> {
                // Two authorities per user: "ROLE_ADMIN" is what
                // hasRole('ADMIN') looks for, the bare "ADMIN" is what
                // hasAuthority('ADMIN') looks for.
                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + claims.role()),
                        new SimpleGrantedAuthority(claims.role()));

                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.username(), null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("SecurityContext populated for username={} role={}",
                        claims.username(), claims.role());
            });
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
