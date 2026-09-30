package com.aditi.order_service.application.command;

/** Input for the "place an order" use case. */
public record CreateOrderCommand(Long productId, Integer quantity, String username) {
}
