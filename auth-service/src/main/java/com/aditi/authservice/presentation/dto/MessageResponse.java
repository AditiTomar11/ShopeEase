package com.aditi.authservice.presentation.dto;

/** A trivial 200/201 body for operations with nothing interesting to return. */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
