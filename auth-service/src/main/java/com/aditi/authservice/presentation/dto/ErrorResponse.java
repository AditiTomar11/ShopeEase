package com.aditi.authservice.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * A single, consistent error shape for the whole service.
 *
 * <p>Consistency here is not cosmetic: a client can handle
 * {@code body.error.code} without first knowing which endpoint it called. The
 * human-readable {@code message} is for logs and the UI; the machine-readable
 * {@code code} is for branching.
 *
 * <p>Validation failures are reported as a list so the form can highlight every
 * bad field at once instead of one error per round trip.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details) {

    public static ErrorResponse of(int status, String error, String code,
                                   String message, String path, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, details);
    }
}
