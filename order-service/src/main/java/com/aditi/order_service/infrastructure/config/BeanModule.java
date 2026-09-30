package com.aditi.order_service.infrastructure.config;

import com.aditi.order_service.domain.port.ProductCatalog;
import com.aditi.order_service.domain.repository.OrderRepository;
import com.aditi.order_service.infrastructure.client.ProductCatalogFeignAdapter;
import com.aditi.order_service.infrastructure.client.ProductClient;
import com.aditi.order_service.infrastructure.persistence.OrderJpaRepository;
import com.aditi.order_service.infrastructure.persistence.OrderRepositoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * THE COMPOSITION ROOT for order-service.
 *
 * <pre>
 *   OrderRepository ← OrderRepositoryImpl  ← OrderJpaRepository  (Spring Data)
 *   ProductCatalog  ← ProductCatalogFeignAdapter ← ProductClient (Feign)
 * </pre>
 *
 * <p>Both adapters are returned as their <em>port</em> type. That is the whole
 * trick of dependency inversion: {@code OrderService} above this line depends on
 * {@code ProductCatalog}, and swapping Feign for a RESTClient, a gRPC stub or an
 * in-memory fake changes only this file.
 */
@Configuration
public class BeanModule {

    @Bean
    @ConditionalOnMissingBean(OrderRepository.class)
    public OrderRepository orderRepository(OrderJpaRepository jpaRepository) {
        return new OrderRepositoryImpl(jpaRepository);
    }

    /**
     * {@code ProductClient} is the Feign-generated proxy; the adapter wraps it to
     * translate transport exceptions into the port's vocabulary.
     */
    @Bean
    @ConditionalOnMissingBean(ProductCatalog.class)
    public ProductCatalog productCatalog(ProductClient productClient) {
        return new ProductCatalogFeignAdapter(productClient);
    }
}
