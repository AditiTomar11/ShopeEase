package com.aditi.order_service.domain.repository;

import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * The domain's view of order storage — an outbound port.
 *
 * <p>Speaks in {@link Order} and {@link OrderStatus}, never in the JPA entity,
 * so the persistence technology stays swappable.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAll();

    List<Order> findByUsername(String username);

    List<Order> findByStatus(OrderStatus status);

    /** @throws com.aditi.order_service.domain.exception.OrderNotFoundException if absent */
    Order update(Long id, Order order);

    void deleteById(Long id);

    /**
     * Looks for an order the same customer already placed for the same product
     * that is still open — used to merge repeat "Add to cart" clicks into one
     * pending order instead of creating duplicates.
     */
    Optional<Order> findOpenOrder(String username, Long productId);
}
