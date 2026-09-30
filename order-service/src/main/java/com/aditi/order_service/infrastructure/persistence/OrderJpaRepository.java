package com.aditi.order_service.infrastructure.persistence;

import com.aditi.order_service.domain.model.OrderStatus;
import com.aditi.order_service.infrastructure.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findFirstByUsernameAndProductIdAndStatus(
            String username, Long productId, OrderStatus status);

    List<OrderEntity> findByUsername(String username);

    List<OrderEntity> findByStatus(OrderStatus status);
}
