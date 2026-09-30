package com.aditi.product_service.infrastructure.config;

import com.aditi.product_service.domain.repository.ProductRepository;
import com.aditi.product_service.infrastructure.persistence.ProductJpaRepository;
import com.aditi.product_service.infrastructure.persistence.ProductRepositoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * THE COMPOSITION ROOT for product-service.
 *
 * <p>Exactly one place where infrastructure classes are attached to domain
 * ports:
 * <pre>
 *   ProductRepository  ← ProductRepositoryImpl ← ProductJpaRepository (Spring Data)
 *   FileStorage        ← StorageConfig (S3FileStorage or LocalFileStorage)
 * </pre>
 *
 * <p>{@code FileStorage} is deliberately <em>not</em> declared here. It is
 * contributed by {@link StorageConfig} under a {@code @ConditionalOnProperty},
 * so this module simply declares a dependency on the port and lets whichever
 * backend matched the properties be injected. Two modules, one rule each:
 * stable wiring here, environment-dependent choice there.
 *
 * <p>{@code @ConditionalOnMissingBean} makes each definition a default, so a
 * {@code @TestConfiguration} can replace the repository with an in-memory fake
 * without any production code changing.
 */
@Configuration
public class BeanModule {

    @Bean
    @ConditionalOnMissingBean(ProductRepository.class)
    public ProductRepository productRepository(ProductJpaRepository jpaRepository) {
        return new ProductRepositoryImpl(jpaRepository);
    }

    // NOTE: there is deliberately no FileStorage bean here.
    //
    // It would be tempting to add a `@ConditionalOnMissingBean` fallback that
    // throws a helpful "set app.storage.type" error, but that is unsafe:
    // @ConditionalOnMissingBean is evaluated while configuration classes are
    // parsed, and two @Configuration classes are parsed in an order that is not
    // guaranteed. If this class were parsed first, no FileStorage bean would
    // exist yet, the fallback would register, and then StorageConfig would add
    // its own — leaving two beans of the same type and a
    // NoUniqueBeanDefinitionException at injection time. Letting the container
    // report "expected a single matching bean but found none" is uglier, and it
    // is always correct.
}
