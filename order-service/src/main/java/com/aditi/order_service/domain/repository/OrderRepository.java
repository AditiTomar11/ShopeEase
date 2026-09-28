package com.aditi.order_service.domain.repository;

import com.aditi.order_service.domain.model.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findAll();
    void deleteById(Long id);
    Order update(Long id, Order order);
    Optional<Order> findByUsernameAndProductIdAndStatus(String username, Long productId, String status);
}