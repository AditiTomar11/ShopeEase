package com.aditi.order_service.infrastructure.persistence;

import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.repository.OrderRepository;
import com.aditi.order_service.infrastructure.entity.OrderEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Autowired
    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = new OrderEntity(
                order.getId(), order.getProductId(), order.getProductName(),
                order.getQuantity(), order.getUsername(), order.getStatus()
        );
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Order update(Long id, Order order) {
        OrderEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + id));
        entity.setProductId(order.getProductId());
        entity.setProductName(order.getProductName());
        entity.setQuantity(order.getQuantity());
        entity.setUsername(order.getUsername());
        entity.setStatus(order.getStatus());
        OrderEntity updated = jpaRepository.save(entity);
        return toDomain(updated);
    }

    @Override
    public Optional<Order> findByUsernameAndProductIdAndStatus(String username, Long productId, String status) {
        return jpaRepository.findByUsernameAndProductIdAndStatus(username, productId, status)
                .map(this::toDomain);
    }

    private Order toDomain(OrderEntity e) {
        return new Order(e.getId(), e.getProductId(), e.getProductName(), e.getQuantity(), e.getUsername(), e.getStatus());
    }
}