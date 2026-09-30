package com.aditi.product_service.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The JSON body accepted by create and update.
 *
 * <p>A DTO, separate from the domain {@code Product}, for one reason: the wire
 * format changes for reasons the business does not care about (a field is
 * renamed, a client wants a flattened shape, a computed field is added). If
 * the domain object were the request body, every one of those changes would
 * ripple into validation rules and business logic.
 *
 * <p>The annotations here are the <em>first</em> line of defence and produce the
 * friendliest error message; the invariants in {@code Product}'s constructor
 * are the second and hold no matter who calls it.
 */
public record ProductRequest(

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "price cannot be negative")
        Double price,

        @Size(max = 1000, message = "imageUrl must be at most 1000 characters")
        String imageUrl,

        @Size(max = 100, message = "category must be at most 100 characters")
        String category,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description) {

    /** Converts the wire format into the domain aggregate. */
    public com.aditi.product_service.domain.model.Product toDomain() {
        return com.aditi.product_service.domain.model.Product
                .create(name, price, imageUrl, category, description);
    }
}
