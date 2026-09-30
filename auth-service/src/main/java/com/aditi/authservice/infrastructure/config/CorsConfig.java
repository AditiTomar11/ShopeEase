package com.aditi.authservice.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS for the auth service.
 *
 * <p>In production the browser only ever talks to the API gateway, which
 * applies CORS itself — so this list exists mainly for local development, where
 * Vite on :5173 hits :8083 directly.
 *
 * <h2>What CORS actually is</h2>
 * A browser-enforced rule that stops one origin's JavaScript from reading
 * another origin's responses. The browser first sends an <em>OPTIONS preflight</em>
 * asking "may I POST to you with an Authorization header?", and only sends the
 * real request if the server answers with matching
 * {@code Access-Control-Allow-Origin} / {@code -Headers} / {@code -Methods}.
 * It is a browser feature: curl and Postman ignore it entirely, which is why a
 * route can "work in Postman and fail in the browser".
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Correlation-Id}")
    private String allowedHeaders;

    @Value("${app.cors.exposed-headers:X-Correlation-Id}")
    private String exposedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Patterns, not origins: a wildcard origin is illegal once credentials
        // are allowed, and "https://*.vercel.app" covers every preview deploy
        // of a Vercel project.
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());

        config.setAllowedMethods(Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());

        config.setAllowedHeaders(Arrays.stream(allowedHeaders.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());

        config.setExposedHeaders(List.of(exposedHeaders.split(",").stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList()));

        config.setAllowCredentials(allowCredentials);
        // Needed so the preflight answer is cached; without it the browser sends
        // an OPTIONS request before every single call.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
