package com.aditi.order_service.infrastructure.client;

import com.aditi.order_service.domain.model.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The raw Feign declaration. Infrastructure only.
 *
 * <h2>What Feign actually does</h2>
 * It reads this interface at start-up and, for every method, generates a
 * dynamic proxy that builds an HTTP request, sends it, deserialises the JSON
 * response, and converts a 4xx/5xx into an exception. That is why a Feign client
 * looks like a plain object to call while doing network I/O underneath.
 *
 * <h2>Why {@code url} and not {@code lb://product-service}}</h2>
 * {@code name = "product-service"} is only an <em>id</em>; the address comes from
 * a load balancer over the Eureka registry. In the deployed setup the address is
 * pinned with {@code url = ${PRODUCT_SERVICE_URL:...}} because:
 * <ul>
 *   <li>Eureka on the free tier sleeps, and a cold Eureka means a cold route;</li>
 *   <li>and a fixed URL makes the dependency obvious in a code review.</li>
 * </ul>
 * The {@code discovery} profile in application.yml switches to {@code lb://} for
 * environments with a warm, highly available registry.
 */
@FeignClient(
        name = "product-service",
        url = "${product.service.url:https://shopeease-product.onrender.com}")
public interface ProductClient {

    /**
     * {@code @PathVariable("id")} names the parameter explicitly. Relying on
     * the name being retained is a classic failure after a refactor or a switch
     * to a different Java compiler setting.
     */
    @GetMapping("/products/{id}")
    Product getProductById(@PathVariable("id") Long id);
}
