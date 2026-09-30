package com.aditi.product_service.domain.repository;

import com.aditi.product_service.domain.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * The domain's view of product storage — an outbound port.
 *
 * <p>Declared in the innermost ring with domain types on both sides, and
 * implemented in {@code infrastructure.persistence.ProductRepositoryImpl}.
 * The domain never sees {@code ProductEntity}, a {@code JpaRepository}, or SQL.
 */
public interface ProductRepository {

    /** Inserts, or updates when the product already carries an id. */
    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    /**
     * @throws com.aditi.product_service.domain.exception.ProductNotFoundException
     *         if no row has that id — documented on the port so callers do not
     *         have to guard defensively.
     */
    Product update(Long id, Product product);

    void deleteById(Long id);

    /** Derived-query-friendly lookup, so filtering happens in SQL, not in Java. */
    List<Product> findByCategory(String category);
}
