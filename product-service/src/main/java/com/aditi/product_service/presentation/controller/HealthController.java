package com.aditi.product_service.presentation.controller;

import com.aditi.product_service.domain.port.FileStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cheap liveness probe — deliberately touches no database.
 *
 * <p>Two different questions are being answered by two different endpoints,
 * and mixing them up is a classic production incident:
 * <ul>
 *   <li><b>Liveness</b> ({@code /health}) — "is this JVM alive?" If it answers
 *       no, the platform should <em>restart</em> the container. It must not
 *       depend on the database, or a brief database outage would restart every
 *       healthy service in the fleet and turn a small problem into an outage.</li>
 *   <li><b>Readiness</b> ({@code /actuator/health/readiness}) — "can this
 *       instance serve traffic right now?" If it answers no, the load balancer
 *       should stop routing to <em>this</em> instance. This one legitimately
 *       checks the database, because an instance that cannot reach Postgres
 *       cannot do its job even though its JVM is fine.</li>
 * </ul>
 *
 * <p>Pointing Render's "Health Check Path" at {@code /health} means Render only
 * routes traffic once the app is really up, and the dashboard's Events tab shows
 * the cold start progressing — which is how you tell "still booting" from
 * "crashed on boot" during a free-tier spin-up.
 */
@RestController
public class HealthController {

    private final FileStorage fileStorage;

    public HealthController(ObjectProvider<FileStorage> fileStorage) {
        this.fileStorage = fileStorage.getIfAvailable();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "product-service");
        // Surfaced because "why is my image 404?" is almost always a
        // storage-backend misconfiguration rather than a code bug.
        body.put("storage", fileStorage == null ? "unconfigured" : fileStorage.describe());
        return body;
    }
}
