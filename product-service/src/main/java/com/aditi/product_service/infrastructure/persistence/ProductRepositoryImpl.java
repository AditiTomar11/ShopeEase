package com.aditi.product_service.infrastructure.persistence;

import com.aditi.product_service.domain.model.Product;
import com.aditi.product_service.domain.repository.ProductRepository;
import com.aditi.product_service.infrastructure.entity.ProductEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapts Spring Data JPA to the domain's {@link ProductRepository} port.
 *
 * <p>The only thing this class does that is interesting is translate between
 * two representations: {@link ProductEntity} (mutable, annotated, has an id)
 * and {@link Product} (immutable, validated, no framework).
 *
 * <p>It carries no stereotype annotation. It is created by {@code BeanModule},
 * the composition root, so the wiring of the whole service can be read in one
 * file rather than inferred from a component scan.
 */
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryImpl(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Product save(Product product) {
        return jpaRepository.save(ProductEntity.from(product)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return jpaRepository.findAll().stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    /**
     * @throws com.aditi.product_service.domain.exception.ProductNotFoundException
     *         if the row is gone. The port documents this, so the use case does
     *         not have to defensively re-check.
     */
    @Override
    @Transactional
    public Product update(Long id, Product product) {
        ProductEntity existing = jpaRepository.findById(id)
                .orElseThrow(() -> new com.aditi.product_service.domain.exception.ProductNotFoundException(id));

        existing.setName(product.getName());
        existing.setPrice(product.getPrice());
        existing.setImageUrl(product.getImageUrl());
        existing.setCategory(product.getCategory());
        existing.setDescription(product.getDescription());

        return jpaRepository.save(existing).toDomain();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByCategory(String category) {
        return jpaRepository.findByCategoryIgnoreCase(category).stream()
                .map(ProductEntity::toDomain)
                .toList();
    }
}
