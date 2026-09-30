package com.aditi.order_service.domain.exception;

/** Base type for expected business failures, mapped to HTTP status codes by the web layer. */
public abstract class OrderDomainException extends RuntimeException {

    private final String code;

    protected OrderDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
