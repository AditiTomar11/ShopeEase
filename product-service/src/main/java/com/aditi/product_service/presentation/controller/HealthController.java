package com.aditi.product_service.presentation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Cheap liveness endpoint that does not touch the database.
 * <p>
 * Point Render's "Health Check Path" at {@code /health} so the dashboard shows when the
 * service is actually up after a cold start, and use it as the target for any
 * keep-warm ping instead of the heavier {@code /products} query.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "product-service"
        );
    }
}
