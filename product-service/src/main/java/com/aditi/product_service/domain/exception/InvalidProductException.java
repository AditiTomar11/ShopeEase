package com.aditi.product_service.domain.exception;

/** The request is well-formed JSON but not a valid product. Mapped to 400. */
public class InvalidProductException extends ProductDomainException {

    public InvalidProductException(String message) {
        super("INVALID_PRODUCT", message);
    }
}
