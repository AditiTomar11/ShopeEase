package com.aditi.product_service.domain.exception;

/**
 * Base type for expected business failures, so the presentation layer can map
 * a specific cause to a specific HTTP status instead of everything becoming a
 * 500.
 */
public abstract class ProductDomainException extends RuntimeException {

    private final String code;

    protected ProductDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
