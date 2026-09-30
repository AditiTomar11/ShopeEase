package com.aditi.order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for order-service.
 *
 * <pre>
 *   presentation  → OrderController, HealthController, error handling
 *   application   → OrderService
 *   domain        → Order, OrderStatus, ports (OrderRepository, ProductCatalog)
 *   infrastructure→ JPA, the Feign client and its adapter, composition root
 * </pre>
 *
 * <p>{@code @EnableFeignClients} scans for {@code @FeignClient} interfaces and
 * registers a proxy bean for each. It is infrastructure wiring on the outer
 * layer — the application layer above it never sees it.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
