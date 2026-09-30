/**
 * DOMAIN LAYER — the innermost ring of order-service.
 *
 * <p>No Spring, no JPA, no Feign, no Jakarta, no JSON annotations. The two
 * outbound ports are {@code OrderRepository} (storage) and
 * {@code ProductCatalog} (the other microservice).
 *
 * <p>The second one is the interesting one: a remote service is modelled as an
 * ordinary port, so the application layer can neither see HTTP nor be tested
 * without it. {@code tools/java-lint.mjs} fails the build if anything in this
 * package imports Spring, JPA, Feign or another service's code.
 */
package com.aditi.order_service.domain;
