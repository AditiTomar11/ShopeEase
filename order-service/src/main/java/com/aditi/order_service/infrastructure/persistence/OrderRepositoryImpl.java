package com.aditi.order_service.infrastructure.persistence;

import com.aditi.order_service.domain.exception.OrderNotFoundException;
import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.model.OrderStatus;
import com.aditi.order_service.domain.repository.OrderRepository;
import com.aditi.order_service.infrastructure.entity.OrderEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapts Spring Data JPA to the domain's {@link OrderRepository} port.
 *
 * <p>One thing worth noticing: the domain declares
 * {@code findOpenOrder(username, productId)} and this class decides that means
 * {@code status = PENDING}. "Open" is a business concept; the query behind it is
 * a storage detail. Putting the {@code PENDING} literal here rather than in the
 * domain keeps the enum out of the repository interface while still expressing
 * the rule once.
 *
 * <p>No stereotype annotation: wired explicitly by {@code BeanModule}.
 */
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        return jpaRepository.save(OrderEntity.from(order)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return jpaRepository.findAll().stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByUsername(String username) {
        return jpaRepository.findByUsername(username).stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return jpaRepository.findByStatus(status).stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public Order update(Long id, Order order) {
        OrderEntity existing = jpaRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        existing.setProductId(order.getProductId());
        existing.setProductName(order.getProductName());
        existing.setQuantity(order.getQuantity());
        existing.setUsername(order.getUsername());
        existing.setStatus(order.getStatus());

        return jpaRepository.save(existing).toDomain();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findOpenOrder(String username, Long productId) {
        return jpaRepository
                .findFirstByUsernameAndProductIdAndStatus(username, productId, OrderStatus.PENDING)
                .map(OrderEntity::toDomain);
    }
}
