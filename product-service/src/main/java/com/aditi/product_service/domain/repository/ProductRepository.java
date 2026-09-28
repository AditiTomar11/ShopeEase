package com.aditi.product_service.domain.repository;

import com.aditi.product_service.domain.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAll();
    void deleteById(Long id);

    Product update(Long id, Product product);
}