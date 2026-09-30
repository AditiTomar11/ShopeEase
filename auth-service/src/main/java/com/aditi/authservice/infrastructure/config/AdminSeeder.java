package com.aditi.authservice.infrastructure.config;

import com.aditi.authservice.domain.model.Role;
import com.aditi.authservice.domain.model.User;
import com.aditi.authservice.domain.port.PasswordHasher;
import com.aditi.authservice.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the bootstrap admin on start-up, if it does not exist yet.
 *
 * <p>A {@link CommandLineRunner} runs after the context is refreshed but inside
 * the application's startup, so it is a reasonable home for a tiny
 * initialisation step like this. Anything longer belongs in a migration
 * (Flyway/Liquibase) — a rule of thumb worth stating out loud in an interview:
 * <em>schema changes go in migrations, not in {@code ddl-auto}</em>.
 *
 * <h2>The security caveat, handled honestly</h2>
 * A hard-coded default password in a public repository is a real finding. The
 * fix used here is not "no default" (which would make a first run impossible)
 * but: read from {@code ADMIN_PASSWORD} when provided, otherwise fall back to
 * the demo password <em>and log a loud warning</em>. Overridable, honest, and
 * testable.
 */
@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.admin.role:ADMIN}")
    private String adminRole;

    @Bean
    public CommandLineRunner seedAdmin(UserRepository userRepository, PasswordHasher passwordHasher) {
        return args -> {
            if (userRepository.existsByUsername(adminUsername)) {
                log.info("Bootstrap admin '{}' already exists — skipping seed", adminUsername);
                return;
            }
            Role role;
            try {
                role = Role.valueOf(adminRole.toUpperCase());
            } catch (IllegalArgumentException badRole) {
                log.warn("Unknown app.admin.role '{}', defaulting to ADMIN", adminRole);
                role = Role.ADMIN;
            }

            userRepository.save(User.newUser(adminUsername, passwordHasher.hash(adminPassword), role));

            if (adminPassword.equals("admin123")) {
                log.warn("=========================================================");
                log.warn(" Bootstrap admin '{}' created with the DEFAULT password.", adminUsername);
                log.warn(" Set ADMIN_PASSWORD before exposing this environment to anyone.");
                log.warn("=========================================================");
            } else {
                log.info("Bootstrap admin '{}' created from ADMIN_PASSWORD", adminUsername);
            }
        };
    }
}
