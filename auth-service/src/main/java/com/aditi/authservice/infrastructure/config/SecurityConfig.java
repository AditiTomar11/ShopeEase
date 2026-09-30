package com.aditi.authservice.infrastructure.config;

import com.aditi.authservice.domain.port.TokenService;
import com.aditi.authservice.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security wiring.
 *
 * <p>Security is infrastructure, so it lives here rather than in the domain —
 * the domain has no opinion about how a caller proves who they are.
 *
 * <h2>STATELESS, and why it is the whole point</h2>
 * {@link SessionCreationPolicy#STATELESS} tells Spring Security never to create
 * or read an {@code HttpServletRequest} session. Without that, a login here
 * would create a session cookie meaningful only on this one instance — and
 * users are load-balanced across several. With a signed, self-contained JWT the
 * server keeps no login state at all, so <em>any</em> instance can validate
 * <em>any</em> request. This is the single most important property that lets
 * services scale horizontally.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * Builds the filter chain.
     *
     * <p>Note that {@code JwtAuthenticationFilter} is constructed inline rather
     * than exposed as its own {@code @Bean}. Spring Boot auto-registers
     * <em>every</em> {@code Filter} bean into the servlet container, so a
     * filter that is both a bean and added to the Spring Security chain runs
     * twice — once in the container chain and once in the security chain. Making
     * it a local variable means it exists in exactly one place, and its
     * position in the chain is stated here in reading order.
     *
     * <p>(Being a {@code OncePerRequestFilter} it would have survived the
     * duplicate via a request attribute — but relying on that guard to hide a
     * wiring mistake is exactly the kind of thing that breaks the day someone
     * swaps in a different filter base class.)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            TokenService tokenService) throws Exception {

        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(tokenService);

        http
                // No cookies are used to carry authentication, so there is no
                // CSRF vector to defend against. (If this service ever starts
                // accepting session cookies, CSRF must be re-enabled.)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: anyone may register or log in.
                        .requestMatchers("/auth/register", "/auth/login", "/actuator/health", "/error")
                        .permitAll()
                        // Everything else needs a valid token. /auth/me is the
                        // worked example of a protected endpoint.
                        .anyRequest().authenticated())
                // Run our filter in the standard chain, just before the
                // username/password filter that normally populates the context.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
