package com.aditi.order_service.application.service;

import com.aditi.order_service.application.command.CreateOrderCommand;
import com.aditi.order_service.application.command.UpdateOrderCommand;
import com.aditi.order_service.domain.exception.OrderNotFoundException;
import com.aditi.order_service.domain.exception.ProductUnavailableException;
import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.model.OrderStatus;
import com.aditi.order_service.domain.model.Product;
import com.aditi.order_service.domain.port.ProductCatalog;
import com.aditi.order_service.domain.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * APPLICATION LAYER — the order use cases.
 *
 * <h2>The important line in this class</h2>
 * <pre>private final ProductCatalog productCatalog;</pre>
 * That is an interface from the domain, not a Feign client. This is the
 * difference between "a microservice calls another microservice" (which is a
 * network detail) and "the order use case needs to know what a product is"
 * (which is a business need). The address of product-service now lives in
 * configuration; the class is unit-testable with a fake; and Feign is one
 * possible implementation rather than a hard dependency.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductCatalog productCatalog;

    public OrderService(OrderRepository orderRepository, ProductCatalog productCatalog) {
        this.orderRepository = orderRepository;
        this.productCatalog = productCatalog;
    }

    /**
     * Places an order, or merges it into the caller's existing open order for
     * the same product.
     *
     * <p>The merge behaviour matters for real users: a customer who clicks
     * "Add to cart" three times should end up with one order for three units,
     * not three competing orders that get dispatched separately.
     *
     * <h2>Why the product is fetched first</h2>
     * We refuse to store an order for something that does not exist, and we
     * snapshot the product's name into the order. That denormalisation is a
     * deliberate trade: the order keeps reading correctly after the product is
     * renamed or deleted, and this service never has to query product-service
     * again just to render a list of orders. The cost is that a rename is not
     * retroactive to old orders — which for order history is usually the
     <em>correct</em> behaviour anyway.
     */
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        String username = command.username() == null ? null : command.username().trim();
        Integer quantity = command.quantity() == null ? 1 : command.quantity();

        Product product = productCatalog.findById(command.productId())
                .orElseThrow(() -> {
                    log.warn("Order rejected: product {} is not available", command.productId());
                    return new ProductUnavailableException(command.productId());
                });

        var openOrder = orderRepository.findOpenOrder(username, product.getId());
        if (openOrder.isPresent()) {
            Order merged = openOrder.get().withQuantity(openOrder.get().getQuantity() + quantity);
            log.info("Merged into existing open order id={} (now qty={})", merged.getId(), merged.getQuantity());
            return orderRepository.update(merged.getId(), merged);
        }

        Order placed = Order.place(product.getId(), product.getName(), quantity, username);
        Order saved = orderRepository.save(placed);

        log.info("Order placed id={} productId={} qty={} customer={}",
                saved.getId(), saved.getProductId(), saved.getQuantity(), username);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUsername(String username) {
        return orderRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Applies a status change, enforcing the legal transitions in the domain.
     *
     * <p>An illegal move (SHIPPED → PENDING) raises {@code InvalidOrderException},
     * which the web layer answers with 409 Conflict: the request was
     * well-formed, it just conflicts with the order's current state.
     */
    @Transactional
    public Order updateStatus(Long id, OrderStatus target) {
        Order existing = getOrder(id);
        Order updated = existing.transitionTo(target);

        log.info("Order {} moved {} -> {}", id, existing.getStatus(), updated.getStatus());
        return orderRepository.update(id, updated);
    }

    /** Full-field update, used by the admin panel. */
    @Transactional
    public Order updateOrder(UpdateOrderCommand command) {
        Order existing = getOrder(command.id());

        Order updated = existing.withDetails(
                command.productId() != null ? command.productId() : existing.getProductId(),
                existing.getProductName(),
                command.quantity() != null ? command.quantity() : existing.getQuantity(),
                command.username() != null ? command.username() : existing.getUsername(),
                command.status() != null ? command.status() : existing.getStatus());

        return orderRepository.update(command.id(), updated);
    }

    @Transactional
    public void deleteOrder(Long id) {
        if (orderRepository.findById(id).isEmpty()) {
            throw new OrderNotFoundException(id);
        }
        orderRepository.deleteById(id);
        log.info("Order {} deleted", id);
    }
}
