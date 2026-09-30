package com.aditi.authservice.infrastructure.config;

import com.aditi.authservice.domain.port.PasswordHasher;
import com.aditi.authservice.domain.port.TokenService;
import com.aditi.authservice.domain.repository.UserRepository;
import com.aditi.authservice.infrastructure.persistence.UserJpaRepository;
import com.aditi.authservice.infrastructure.persistence.UserRepositoryImpl;
import com.aditi.authservice.infrastructure.security.BCryptPasswordHasher;
import com.aditi.authservice.infrastructure.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * THE COMPOSITION ROOT — the single place where the onion's layers are wired
 * together.
 *
 * <h2>Why not just annotate the implementations?</h2>
 * Annotating {@code @Repository} / {@code @Component} on the adapters and
 * letting component scanning find them works, and is what most codebases do.
 * The cost is that the wiring becomes <em>implicit and invisible</em>: you have
 * to know which packages are scanned to know what actually gets injected.
 *
 * <p>Declaring the beans here makes the whole dependency graph of the service
 * readable on one screen:
 * <pre>
 *   UserRepository  ← UserRepositoryImpl ← UserJpaRepository (Spring Data)
 *   PasswordHasher  ← BCryptPasswordHasher
 *   TokenService    ← JwtTokenService
 * </pre>
 *
 * <h2>Which bean wins if there are two?</h2>
 * This is the "dependency injection" topic in one annotation:
 * {@code @ConditionalOnMissingBean} makes each of these definitions a
 * <em>default</em>. Add a {@code @Profile("test")} or a {@code @TestConfiguration}
 * class that declares its own {@code PasswordHasher}, and it replaces the
 * production one with no edit here. That is how a real integration test swaps
 * BCrypt for a fast, deterministic hasher.
 *
 * <h2>Bind by type, not by name</h2>
 * Each method returns the <em>port</em> type ({@code PasswordHasher}), not the
 * implementation. Everything above therefore depends on the abstraction, so
 * swapping the concrete adapter is invisible to the application layer. Had we
 * returned {@code BCryptPasswordHasher} directly, every consumer would now be
 * coupled to BCrypt and the inversion would be gone.
 */
@Configuration
public class BeanModule {

    /**
     * Outbound port: domain wants user storage → infrastructure supplies JPA.
     * The returned type is the domain interface, so the arrow of the dependency
     * points inwards even though the implementation is outside.
     */
    @Bean
    @ConditionalOnMissingBean(UserRepository.class)
    public UserRepository userRepository(UserJpaRepository jpaRepository) {
        return new UserRepositoryImpl(jpaRepository);
    }

    /** Outbound port: domain wants password hashing → infrastructure supplies BCrypt. */
    @Bean
    @ConditionalOnMissingBean(PasswordHasher.class)
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    /**
     * Outbound port: domain wants tokens → infrastructure supplies JJWT.
     * Configuration values are injected here and passed through the constructor,
     * so {@link JwtTokenService} itself needs no {@code @Value} annotations and
     * stays trivially unit-testable.
     */
    @Bean
    @ConditionalOnMissingBean(TokenService.class)
    public TokenService tokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMillis,
            @Value("${jwt.issuer:shopease-auth-service}") String issuer) {
        return new JwtTokenService(secret, expirationMillis, issuer);
    }
}
