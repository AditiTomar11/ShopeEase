package com.aditi.order_service.domain.exception;

/** No order has that id. Mapped to 404. */
public class OrderNotFoundException extends OrderDomainException {

    public OrderNotFoundException(Long id) {
        super("ORDER_NOT_FOUND", "Order not found with id " + id);
    }
}
