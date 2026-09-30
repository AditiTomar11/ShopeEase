package com.aditi.order_service.domain.port;

import com.aditi.order_service.domain.model.Product;

import java.util.Optional;

/**
 * Outbound port for "ask the product service what a product is".
 *
 * <h2>Why this interface exists at all</h2>
 * Before it existed, {@code OrderService} imported
 * {@code ProductClient} — a Feign interface — directly from the infrastructure
 * layer. That is a layering violation, and it is worth being able to explain
 * precisely why, because it is a very common mistake:
 * <ul>
 *   <li>the application layer now depends on an HTTP client library, so it can
 *       only ever be exercised over the network;</li>
 *   <li>its unit test has to stand up a server or mock Feign internals;</li>
 *   <li>and Feign's {@code @FeignClient(name=..., url=...)} annotation means the
 *       <em>address of another service</em> is baked into application logic,
 *       where a config change belongs.</li>
 * </ul>
 *
 * <p>Declaring the port in the domain and implementing it in infrastructure
 * ({@code ProductCatalogFeignAdapter}) puts the address where configuration
 * belongs and makes the use case testable with a two-line fake.
 *
 * <h2>It returns Optional, not a thrown exception</h2>
 * "That product does not exist" is an expected outcome of a lookup, not an
 * exceptional one, so the port models it as a value. The adapter turns the
 * HTTP 404 into {@link Optional#empty()}; the use case decides what that means
 * for an order.
 */
public interface ProductCatalog {

    Optional<Product> findById(Long id);
}
