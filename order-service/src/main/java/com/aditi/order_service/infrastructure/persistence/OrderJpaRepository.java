package com.aditi.order_service.infrastructure.persistence;

import com.aditi.order_service.infrastructure.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByUsernameAndProductIdAndStatus(String username, Long productId, String status);
}