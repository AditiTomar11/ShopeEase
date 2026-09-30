package com.aditi.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * The gateway's authorisation rules, as configuration.
 *
 * <h2>Why rules live in config, not in code</h2>
 * An access-control matrix is a <em>product</em> decision that changes far more
 * often than the code implementing it. Keeping it in {@code application.yml}
 * means adding a protected endpoint is a one-line change reviewed by whoever owns
 * security policy, not a code change requiring a build and deploy. It also makes
 * the whole policy readable in one screen — which is exactly what a security
 * review wants to see.
 *
 * <h2>Order matters</h2>
 * Rules are evaluated top to bottom and the first match wins, exactly like
 * Spring Security's matcher chain. A broad rule placed above a narrow one
 * silently swallows it, so the lists below are ordered narrow → broad.
 *
 * @param publicPaths            paths anyone may call (no token needed)
 * @param publicMethods          HTTP methods considered public; anything else on a
 *                               public path still needs a token
 * @param adminPaths             paths that require the ADMIN role
 * @param authenticatedPaths     paths that require any valid token
 * @param stripAuthorization     remove the Authorization header before forwarding
 * @param forwardIdentityHeaders add X-User-Name / X-User-Role for downstream services
 */
@ConfigurationProperties(prefix = "app.security")
public record GatewaySecurityProperties(

        @DefaultValue({"/auth/login", "/auth/register", "/auth/health",
                "/products", "/products/images/limits", "/actuator/health",
                "/health", "/products/category/**", "/orders/health"})
        List<String> publicPaths,

        @DefaultValue({"GET", "HEAD", "OPTIONS"}) List<String> publicMethods,

        @DefaultValue({"/products/images/**", "/products/*/image", "/products/with-image"})
        List<String> adminPaths,

        @DefaultValue({"/orders", "/orders/**", "/products/**", "/auth/me"})
        List<String> authenticatedPaths,

        @DefaultValue("false") boolean stripAuthorization,

        @DefaultValue("true") boolean forwardIdentityHeaders) {
}
