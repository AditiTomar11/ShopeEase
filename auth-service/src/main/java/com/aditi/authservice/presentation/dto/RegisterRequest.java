package com.aditi.authservice.presentation.dto;

import com.aditi.authservice.domain.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The JSON body of {@code POST /auth/register}.
 *
 * <p>Bean Validation runs before the controller body executes, so a malformed
 * request never reaches the use case. Declaring the constraints as annotations
 * keeps the rule next to the field it protects, and means the OpenAPI/Jakarta
 * docs are generated from the same source of truth.
 */
public record RegisterRequest(

        @NotBlank(message = "username is required")
        @Size(max = 100, message = "username must be at most 100 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 6, max = 72, message = "password must be 6-72 characters")
        String password,

        /**
         * Optional. Absent means CUSTOMER. Recognised values are CUSTOMER and
         * ADMIN — anything else is rejected with 400 rather than silently
         * coerced.
         */
        String role) {

    public Role toRoleOrDefault() {
        if (role == null || role.isBlank()) {
            return Role.CUSTOMER;
        }
        return Role.valueOf(role.trim().toUpperCase());
    }
}
