package com.aditi.order_service.presentation.dto;

import com.aditi.order_service.application.command.UpdateOrderCommand;
import com.aditi.order_service.domain.model.OrderStatus;
import jakarta.validation.constraints.Min;

/** JSON body of {@code PUT /orders/{id}}. */
public record UpdateOrderRequest(
        Long productId,
        @Min(value = 1, message = "quantity must be at least 1") Integer quantity,
        String username,
        OrderStatus status) {

    public UpdateOrderCommand toCommand(Long id) {
        return new UpdateOrderCommand(id, productId, quantity, username, status);
    }
}
