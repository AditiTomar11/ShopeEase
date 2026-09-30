package com.aditi.order_service.application.command;

import com.aditi.order_service.domain.model.OrderStatus;

/** Input for the "update an order" use case. */
public record UpdateOrderCommand(
        Long id,
        Long productId,
        Integer quantity,
        String username,
        OrderStatus status) {
}
