package com.aditi.product_service.presentation.exception;

import com.aditi.product_service.domain.exception.EmptyUploadException;
import com.aditi.product_service.domain.exception.ImageTooLargeException;
import com.aditi.product_service.domain.exception.InvalidProductException;
import com.aditi.product_service.domain.exception.ProductDomainException;
import com.aditi.product_service.domain.exception.ProductNotFoundException;
import com.aditi.product_service.domain.exception.StorageException;
import com.aditi.product_service.domain.exception.UnsupportedImageTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single translation point from domain exceptions to HTTP status codes.
 *
 * <h2>The status codes, and why each one is the right answer</h2>
 * <ul>
 *   <li><b>400</b> — the request is malformed or breaks a business rule.
 *       Validation failures list every offending field at once so the form can
 *       highlight them all instead of one per round trip.</li>
 *   <li><b>404</b> — the product does not exist. Distinct from 400 because
 *       "retry with different data" and "stop, this will never work" call for
 *       different client behaviour.</li>
 *   <li><b>409 Conflict</b> — the request is valid but clashes with current
 *       state. The textbook example is a resource-lock conflict.</li>
 *   <li><b>413</b> — the body exceeded the container's upload limit. Note this
 *       is distinct from our own {@link ImageTooLargeException}: the container
 *       rejects the request before our code ever runs, so it surfaces as a
 *       different exception and needs its own handler.</li>
 *   <li><b>415</b> — the media type is not one we accept. The response should
 *       say which types are allowed, which is what {@code Accept-Post} is for.</li>
 *   <li><b>500</b> — an actual bug. Logged with a full stack trace, answered
 *       with a generic message, because a stack trace can leak table names,
 *       file paths and library versions.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ProductNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidProductException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidProduct(InvalidProductException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedType(UnsupportedImageTypeException ex,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ImageTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(ImageTooLargeException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /**
     * Raised by the servlet container when the request body exceeds
     * {@code spring.servlet.multipart.max-file-size}, i.e. before any of our
     * code runs. Handled explicitly so the client gets the same error shape as
     * our own size check rather than a raw 500 from the container.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleContainerLimit(MaxUploadSizeExceededException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE",
                "The uploaded file exceeds the server's maximum request size", request, List.of());
    }

    /** The multipart body had no {@code file} part at all. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_FILE",
                "Expected a multipart part named 'file'", request, List.of());
    }

    @ExceptionHandler(EmptyUploadException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyUpload(EmptyUploadException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /**
     * S3 (or the disk) failed. The cause is already logged in full by
     * {@code S3FileStorage}; the client gets a sanitised 502, because "Bad
     * Gateway" accurately describes a downstream dependency failing and
     * signals to the caller that retrying later may work.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(StorageException ex,
                                                             HttpServletRequest request) {
        log.error("Storage failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getCode(),
                "The image could not be stored. Please try again.", request, List.of());
    }

    /** Any other anticipated business rule. */
    @ExceptionHandler(ProductDomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(ProductDomainException ex,
                                                            HttpServletRequest request) {
        log.warn("Domain rule violated: code={} message={}", ex.getCode(), ex.getMessage());
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "Request body could not be parsed", request, List.of());
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
