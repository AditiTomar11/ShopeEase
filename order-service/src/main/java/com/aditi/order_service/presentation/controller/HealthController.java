package com.aditi.order_service.presentation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness probe at {@code /health}.
 *
 * <p>Kept in its own controller rather than under {@code /orders} so it can
 * never be shadowed by the {@code /orders/{id}} mapping — a real hazard when a
 * Long path variable shares a prefix with a literal route.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "order-service");
        return body;
    }
}
