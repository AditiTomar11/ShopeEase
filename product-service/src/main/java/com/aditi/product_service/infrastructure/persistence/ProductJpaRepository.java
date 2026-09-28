package com.aditi.product_service.infrastructure.persistence;

import com.aditi.product_service.infrastructure.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
}