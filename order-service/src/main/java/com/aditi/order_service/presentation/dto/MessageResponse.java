package com.aditi.order_service.presentation.dto;

/** A simple message body for operations with nothing structured to return. */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
