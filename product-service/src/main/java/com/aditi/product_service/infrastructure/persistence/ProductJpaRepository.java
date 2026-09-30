package com.aditi.product_service.infrastructure.persistence;

import com.aditi.product_service.infrastructure.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA interface. Never referenced outside this package — the
 * application layer talks to {@code ProductRepository} instead.
 */
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    /**
     * Derived query: Spring Data parses the method name and generates the SQL.
     * That convenience is also the reason it stays here rather than in the
     * domain — a query method is a persistence detail, and a hand-written
     * {@code @Query} would drag JPQL (a SQL dialect) into the domain.
     */
    List<ProductEntity> findByCategoryIgnoreCase(String category);
}
