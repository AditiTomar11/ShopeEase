package com.aditi.order_service.domain;

import com.aditi.order_service.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the order lifecycle rules.
 *
 * <p>Small, but this is the class that protects real money: an order that can go
 * from SHIPPED back to PENDING is an order that gets dispatched twice.
 */
class OrderStatusTest {

    @Test
    @DisplayName("PENDING may move to PROCESSING or CANCELLED")
    void pendingTransitions() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.PROCESSING));
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED));
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("PROCESSING may move to SHIPPED or CANCELLED")
    void processingTransitions() {
        assertTrue(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.SHIPPED));
        assertTrue(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("SHIPPED may only move to DELIVERED")
    void shippedTransitions() {
        assertTrue(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PENDING));
        assertFalse(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PROCESSING));
        assertFalse(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("DELIVERED and CANCELLED are terminal")
    void terminalStates() {
        for (OrderStatus target : OrderStatus.values()) {
            assertFalse(OrderStatus.DELIVERED.canTransitionTo(target));
            assertFalse(OrderStatus.CANCELLED.canTransitionTo(target));
        }
    }

    @Test
    @DisplayName("a status is never a transition to itself")
    void noSelfTransition() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(status.canTransitionTo(status));
        }
    }

    @Test
    @DisplayName("a null target is never valid")
    void nullIsInvalid() {
        assertFalse(OrderStatus.PENDING.canTransitionTo(null));
    }

    @Test
    @DisplayName("parses stored values leniently, falling back for unknown text")
    void parsesLeniently() {
        assertEquals(OrderStatus.SHIPPED, OrderStatus.fromOrDefault("SHIPPED", OrderStatus.PENDING));
        assertEquals(OrderStatus.SHIPPED, OrderStatus.fromOrDefault("shipped", OrderStatus.PENDING));
        assertEquals(OrderStatus.SHIPPED, OrderStatus.fromOrDefault("  Shipped  ", OrderStatus.PENDING));
        // Old rows could contain anything; never blow up while reading them.
        assertEquals(OrderStatus.PENDING, OrderStatus.fromOrDefault("WEIRD", OrderStatus.PENDING));
        assertEquals(OrderStatus.PENDING, OrderStatus.fromOrDefault(null, OrderStatus.PENDING));
        assertEquals(OrderStatus.PENDING, OrderStatus.fromOrDefault("", OrderStatus.PENDING));
    }
}
