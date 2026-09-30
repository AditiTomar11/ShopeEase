package com.aditi.order_service.infrastructure.entity;

import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.model.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The JPA entity for an order.
 *
 * <p>{@code productName} is a denormalised snapshot taken when the order was
 * placed, not a foreign key join. That is intentional: the order history must
 * keep showing what was actually bought even after the product is renamed or
 * removed, and listing orders then costs one query instead of one per row.
 * The trade is that a product rename does not update old orders, which for order
 * history is usually what you want.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 100)
    private String username;

    /**
     * Stored as a VARCHAR holding the enum name. Older rows already contain
     * "PENDING"/"SHIPPED"/… so no migration is needed; the lenient
     * {@code fromOrDefault} in {@code toDomain()} guards against any value an
     * older version of the service might have written.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    protected OrderEntity() {
        // required by JPA
    }

    public OrderEntity(Long id, Long productId, String productName, Integer quantity,
                       String username, OrderStatus status) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.username = username;
        this.status = status;
    }

    public Order toDomain() {
        return Order.place(productId, productName, quantity, username)
                .transitionToOrKeep(status);
    }

    public static OrderEntity from(Order order) {
        return new OrderEntity(order.getId(), order.getProductId(), order.getProductName(),
                order.getQuantity(), order.getUsername(), order.getStatus());
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
