package com.aditi.order_service.domain.model;

import com.aditi.order_service.domain.exception.InvalidOrderException;

import java.util.Objects;

/**
 * The Order aggregate.
 *
 * <p>Immutable and self-validating, for the same reasons as Product: a rule
 * enforced in the constructor cannot be bypassed by any entry point, and there
 * is no half-updated order for a concurrent request to observe.
 *
 * <p>Note what is <em>not</em> here: no price, no stock level, no shipping
 * address. An order stores only the product's id and a denormalised snapshot of
 * its name. See {@link com.aditi.order_service.domain.port.ProductCatalog} for
 * why duplicating that name into this service is the correct choice.
 */
public class Order {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final Integer quantity;
    private final String username;
    private final OrderStatus status;

    private Order(Long id, Long productId, String productName, Integer quantity,
                  String username, OrderStatus status) {
        this.id = id;
        this.productId = requireProductId(productId);
        this.productName = productName == null ? null : productName.trim();
        this.quantity = requireQuantity(quantity);
        this.username = requireUsername(username);
        this.status = status == null ? OrderStatus.PENDING : status;
    }

    public static Order place(Long productId, String productName, Integer quantity, String username) {
        return new Order(null, productId, productName, quantity, username, OrderStatus.PENDING);
    }

    /**
     * Rehydrates an order with a status that was read from storage, bypassing
     * the transition rules.
     *
     * <p>Those rules constrain <em>changes</em>, not <em>loading</em>: a row
     * already saved as SHIPPED must come back as SHIPPED even though
     * PENDING → SHIPPED is not a legal single hop. Only the persistence adapter
     * should call this, and only when reading.
     */
    public Order transitionToOrKeep(OrderStatus stored) {
        if (stored == null || stored == this.status) {
            return this;
        }
        return new Order(id, productId, productName, quantity, username, stored);
    }

    public Order withId(Long newId) {
        return new Order(newId, productId, productName, quantity, username, status);
    }

    public Order withQuantity(Integer newQuantity) {
        return new Order(id, productId, productName, newQuantity, username, status);
    }

    /**
     * Applies a status change after checking the transition is legal.
     *
     * @throws InvalidOrderException when the move is not permitted
     */
    public Order transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderException(
                    "Cannot change an order from " + status + " to " + target);
        }
        return new Order(id, productId, productName, quantity, username, target);
    }

    public Order withDetails(Long productId, String productName, Integer quantity,
                             String username, OrderStatus status) {
        return new Order(id, productId, productName, quantity, username, status);
    }

    private static Long requireProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new InvalidOrderException("productId must be a positive number");
        }
        return productId;
    }

    private static Integer requireQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidOrderException("quantity must be at least 1");
        }
        if (quantity > 999) {
            throw new InvalidOrderException("quantity must not exceed 999");
        }
        return quantity;
    }

    private static String requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidOrderException("username is required");
        }
        return username.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getUsername() {
        return username;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Order other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", productId=" + productId + ", qty=" + quantity
                + ", username='" + username + "', status=" + status + '}';
    }
}
