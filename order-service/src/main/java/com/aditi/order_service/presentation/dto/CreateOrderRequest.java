package com.aditi.order_service.presentation.dto;

import com.aditi.order_service.application.command.CreateOrderCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** JSON body of {@code POST /orders}. */
public record CreateOrderRequest(

        @NotNull(message = "productId is required")
        Long productId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotBlank(message = "username is required")
        String username) {

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(productId, quantity, username);
    }
}
