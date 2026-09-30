package com.aditi.product_service.domain.exception;

/** A product id does not exist. Mapped to 404. */
public class ProductNotFoundException extends ProductDomainException {

    public ProductNotFoundException(Long id) {
        super("PRODUCT_NOT_FOUND", "Product not found with id " + id);
    }
}
