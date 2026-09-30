package com.aditi.order_service.presentation.exception;

import com.aditi.order_service.domain.exception.InvalidOrderException;
import com.aditi.order_service.domain.exception.OrderDomainException;
import com.aditi.order_service.domain.exception.OrderNotFoundException;
import com.aditi.order_service.domain.exception.ProductUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Maps domain exceptions to HTTP status codes.
 *
 * <p>Two mappings here are worth being able to justify out loud:
 * <ul>
 *   <li>{@link ProductUnavailableException} → <b>502 Bad Gateway</b>, because
 *       this service is a client of another service and <em>it</em> is what is
 *       broken. A 404 would tell the caller the product is gone for good and
 *       stop them retrying.</li>
 *   <li>{@link InvalidOrderException} → <b>400</b> for a malformed order but
 *       <b>409</b> when it describes an illegal status transition, because "you
 *       sent something invalid" and "your request conflicts with the current
 *       state" call for different client behaviour.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(OrderNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleProductUnavailable(ProductUnavailableException ex,
                                                                         HttpServletRequest request) {
        log.error("Downstream dependency failure on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.BAD_GATEWAY, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrder(InvalidOrderException ex,
                                                                  HttpServletRequest request) {
        boolean isTransitionProblem = ex.getMessage() != null
                && ex.getMessage().startsWith("Cannot change an order");
        HttpStatus status = isTransitionProblem ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return build(status, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(OrderDomainException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        String message = details.isEmpty() ? "Validation failed" : String.join("; ", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, details);
    }

    /**
     * e.g. {@code /orders/status/DELIVERED} where the word is not a valid
     * {@code OrderStatus}. Spring fails converting the path variable and this
     * turns it into a readable 400 instead of a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                   HttpServletRequest request) {
        String message = "'" + ex.getValue() + "' is not a valid value for '"
                + ex.getName() + "' (expected one of PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED)";
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "Request body could not be parsed. Check that status is one of "
                        + "PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED.",
                request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex,
                                                                    HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return build(resolved, resolved.name(), ex.getReason(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again.", request, List.of());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message,
                                                      HttpServletRequest request, List<String> details) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "code", code == null ? status.name() : code,
                "message", message == null ? status.getReasonPhrase() : message,
                "path", request.getRequestURI(),
                "details", details));
    }
}
