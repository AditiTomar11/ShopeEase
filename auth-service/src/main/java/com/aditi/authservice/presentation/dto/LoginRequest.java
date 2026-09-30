package com.aditi.authservice.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** The JSON body of {@code POST /auth/login}. */
public record LoginRequest(

        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password) {
}
