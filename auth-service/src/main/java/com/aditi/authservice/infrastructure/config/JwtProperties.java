package com.aditi.authservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Type-safe binding for the {@code jwt.*} configuration block.
 *
 * <p>Prefer this over sprinkling {@code @Value("${…}")} across the code: the
 * property names are checked at compile time by the IDE, the values are parsed
 * and converted once, and {@code meta.ignore-unknown} / validation can reject a
 * misconfigured deployment at start-up.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("86400000") long expiration,
        @DefaultValue("shopease-auth-service") String issuer) {
}
