package com.aditi.order_service.infrastructure.client;

import com.aditi.order_service.domain.model.Product;
import com.aditi.order_service.domain.port.ProductCatalog;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Adapts the Feign client to the domain's {@link ProductCatalog} port.
 *
 * <h2>The most valuable method here is the {@code catch}</h2>
 * Feign turns <em>every</em> non-2xx response into a {@link FeignException}, so
 * a perfectly ordinary "no such product" (404) arrives as an exception. If this
 * adapter let that escape, the application layer would need a try/catch around
 * every lookup and would have to know Feign's exception hierarchy — leaking the
 * transport into the business logic, which is the exact thing the port exists to
 * prevent.
 *
 * <p>Instead the port's contract is honoured: an absent product is
 * {@link Optional#empty()}, an <em>unreachable</em> product service is a
 * {@code ProductUnavailableException}. The use case then makes a clean decision
 * about each, with no knowledge of HTTP status codes.
 */
public class ProductCatalogFeignAdapter implements ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogFeignAdapter.class);

    private final ProductClient productClient;

    public ProductCatalogFeignAdapter(ProductClient productClient) {
        this.productClient = productClient;
    }

    @Override
    public Optional<Product> findById(Long id) {
        try {
            return Optional.ofNullable(productClient.getProductById(id));

        } catch (FeignException.NotFound notFound) {
            // A normal, expected outcome: no such product.
            log.warn("Product {} does not exist", id);
            return Optional.empty();

        } catch (FeignException serviceDown) {
            // 5xx, timeout, connection refused, DNS failure. This one is NOT an
            // empty result — it is an outage, and the caller must be told the
            // difference. If we returned empty here, a total product-service
            // outage would look like "every product was deleted", and the order
            // service would happily accept orders for a product that exists.
            log.error("Product service is unreachable while loading product {}: {}",
                    id, serviceDown.getMessage());
            throw new com.aditi.order_service.domain.exception.ProductUnavailableException(id);

        } catch (RuntimeException unexpected) {
            log.error("Unexpected failure loading product {}", id, unexpected);
            throw new com.aditi.order_service.domain.exception.ProductUnavailableException(id);
        }
    }
}
