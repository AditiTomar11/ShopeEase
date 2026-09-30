package com.aditi.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for api-gateway.
 *
 * <h2>What this service is, precisely</h2>
 * A <b>reverse proxy</b> plus a <b>security checkpoint</b>. It owns no business
 * logic and no database. Every request from the browser enters here and is
 * forwarded to exactly one downstream service.
 *
 * <h2>Why centralise routing at all?</h2>
 * <ul>
 *   <li>one origin, so the browser makes same-origin requests and CORS becomes a
 *       non-issue;</li>
 *   <li>one place where authentication is enforced, so a new service cannot
 *       accidentally be left unprotected;</li>
 *   <li>one place to add cross-cutting concerns — rate limiting, caching,
 *       request size caps, WAF rules, a circuit breaker — without touching the
 *       business services;</li>
 *   <li>and the services' own hostnames never have to be exposed to the client.</li>
 * </ul>
 *
 * <p>Reactive, not servlet: the gateway is I/O bound (it spends its life waiting
 * on the network), so WebFlux's event loop handles far more concurrent
 * connections per thread than a thread-per-request servlet container. The
 * business services, which do block on JDBC, stay on Spring MVC.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
