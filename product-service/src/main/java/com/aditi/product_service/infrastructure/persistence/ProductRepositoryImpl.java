package com.aditi.product_service.infrastructure.persistence;

import com.aditi.product_service.domain.model.Product;
import com.aditi.product_service.domain.repository.ProductRepository;
import com.aditi.product_service.infrastructure.entity.ProductEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    @Autowired
    public ProductRepositoryImpl(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = new ProductEntity(
                product.getId(), product.getName(), product.getPrice(),
                product.getImageUrl(), product.getCategory(), product.getDescription()
        );
        ProductEntity saved = jpaRepository.save(entity);
        return new Product(
                saved.getId(), saved.getName(), saved.getPrice(),
                saved.getImageUrl(), saved.getCategory(), saved.getDescription()
        );
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id)
                .map(e -> new Product(
                        e.getId(), e.getName(), e.getPrice(),
                        e.getImageUrl(), e.getCategory(), e.getDescription()
                ));
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream()
                .map(e -> new Product(
                        e.getId(), e.getName(), e.getPrice(),
                        e.getImageUrl(), e.getCategory(), e.getDescription()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Product update(Long id, Product product) {
        ProductEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id " + id));
        entity.setName(product.getName());
        entity.setPrice(product.getPrice());
        entity.setImageUrl(product.getImageUrl());
        entity.setCategory(product.getCategory());
        entity.setDescription(product.getDescription());
        ProductEntity updated = jpaRepository.save(entity);
        return new Product(
                updated.getId(), updated.getName(), updated.getPrice(),
                updated.getImageUrl(), updated.getCategory(), updated.getDescription()
        );
    }
}