package com.aditi.order_service.domain.exception;

/**
 * The product the order refers to does not exist, or the product service could
 * not be reached.
 *
 * <p>Mapped to 502, not 404: the order itself is fine, a <em>downstream</em>
 * dependency failed. A 502 tells the caller this is worth retrying; a 404 would
 * tell them the thing they asked for is gone for good.
 */
public class ProductUnavailableException extends OrderDomainException {

    public ProductUnavailableException(Long productId) {
        super("PRODUCT_UNAVAILABLE",
                "Product " + productId + " could not be loaded. Please try again.");
    }
}
