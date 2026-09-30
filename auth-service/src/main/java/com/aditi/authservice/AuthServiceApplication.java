package com.aditi.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for auth-service.
 *
 * <p>Package layout (the onion, outside in):
 * <pre>
 *   presentation  → HTTP: controllers, DTOs, error handling
 *   application   → use cases: AuthService
 *   domain        → model, repository ports, outbound ports, business exceptions
 *   infrastructure→ JPA entities, BCrypt, JJWT, security, logging, composition root
 * </pre>
 *
 * <p>EnableDiscoveryClient registers this instance with Eureka so the gateway
 * and other services can find it by name. It sets nothing else up; discovery is
 * just an address book, and the gateway is the only component that actually
 * uses it in this system.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
