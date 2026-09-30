package com.aditi.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS lives at the gateway, and only at the gateway.
 *
 * <h2>Why one place</h2>
 * CORS is a browser-facing policy about <em>who may talk to the API</em>. The
 * browser only ever talks to this service, so this is the only place that can
 * possibly answer a preflight. Duplicating the policy in four business services
 * means four places to update and four chances to get it subtly wrong.
 *
 * <h2>What a preflight is</h2>
 * Before a browser sends a cross-origin request that is "non-simple" (anything
 * with a custom header such as {@code Authorization}, or a JSON content type, or
 * a method other than GET/POST), it sends an {@code OPTIONS} request with an
 * {@code Origin} header and asks permission. Only if this service answers with
 * matching {@code Access-Control-Allow-*} headers does the real request follow.
 * That is why the gateway's JWT filter explicitly skips preflights: the browser
 * will not attach a token to the permission request.
 *
 * <h2>The two traps</h2>
 * <ul>
 *   <li>{@code Access-Control-Allow-Origin: *} is <b>illegal</b> once
 *       {@code Allow-Credentials} is true. Wildcards are expressed with
 *       {@code allowedOriginPatterns} instead, which also covers
 *       {@code https://*.vercel.app} for every preview deployment of a Vercel
 *       project.</li>
 *   <li>Headers the browser needs to <em>read</em> must be listed in
 *       {@code exposedHeaders} — allowed to send is not the same as allowed to
 *       read. {@code X-Correlation-Id} is exposed here so the frontend can show
 *       the request id on an error screen.</li>
 * </ul>
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

    /**
     * A {@code CorsWebFilter} (reactive) rather than
     * {@code addCorsMappings} (servlet) — this service is WebFlux, and a servlet
     * API here simply does not exist.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(split(allowedOrigins));
        config.setAllowedMethods(split(allowedMethods));
        config.setAllowedHeaders(split(allowedHeaders));
        config.setExposedHeaders(split(exposedHeaders));
        config.setAllowCredentials(allowCredentials);
        // Cache the preflight answer, otherwise the browser sends an OPTIONS
        // round trip before every single API call.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /**
     * Also exposed as a {@link CorsConfigurationSource} bean so Spring Security's
     * reactive support can pick it up. Cheap, and it removes a class of
     * "works for /products but not for /orders" bugs.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(split(allowedOrigins));
        config.setAllowedMethods(split(allowedMethods));
        config.setAllowedHeaders(split(allowedHeaders));
        config.setExposedHeaders(split(exposedHeaders));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
