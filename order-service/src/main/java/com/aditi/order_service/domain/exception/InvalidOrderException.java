package com.aditi.order_service.domain.exception;

/** The request breaks a business rule. Mapped to 400, or 409 for an illegal status transition. */
public class InvalidOrderException extends OrderDomainException {

    public InvalidOrderException(String message) {
        super("INVALID_ORDER", message);
    }
}
