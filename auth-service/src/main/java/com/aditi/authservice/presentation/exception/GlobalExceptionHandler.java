package com.aditi.authservice.presentation.exception;

import com.aditi.authservice.domain.exception.AuthDomainException;
import com.aditi.authservice.domain.exception.InvalidCredentialsException;
import com.aditi.authservice.domain.exception.UsernameAlreadyExistsException;
import com.aditi.authservice.presentation.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Turns exceptions into HTTP responses, in one place.
 *
 * <h2>Why bother</h2>
 * Without a handler, Spring Boot's default turns any uncaught exception into a
 * 500 with a generic body — so "username already taken" would come back as
 * <em>Internal Server Error</em>, the UI could not tell the user what went
 * wrong, and every error type needed bespoke handling at each call site.
 *
 * <p>{@code @RestControllerAdvice} is the AOP equivalent of a servlet filter:
 * it wraps every controller, so handlers can be written once.
 *
 * <h2>The rule this class encodes</h2>
 * Exceptions are for <em>exceptional</em> cases. A duplicate username is a
 * perfectly normal outcome of a valid request, so the domain throws a named
 * exception and this class maps it to 409. Genuine bugs (a NullPointerException
 * from a bug) still fall through to the 500 branch — logged in full for us,
 * sanitised for the caller, because stack traces and SQL fragments must never
 * reach a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Duplicate username → 409 Conflict. */
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameTaken(UsernameAlreadyExistsException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /** Wrong username or password → 401 Unauthorized. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /** Any other anticipated business failure → 400. */
    @ExceptionHandler(AuthDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(AuthDomainException ex,
                                                      HttpServletRequest request) {
        log.warn("Domain rule violated: code={} message={}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /** Bad input from a client, e.g. a 5-character password. → 400 with every failing field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        String message = details.isEmpty() ? "Validation failed" : String.join("; ", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                details.isEmpty() ? "Validation failed" : String.join("; ", details),
                request, details);
    }

    /** Malformed JSON, or a JSON value of the wrong type (e.g. role:"GURU"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "Request body could not be parsed", request, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), request, List.of());
    }

    /** Authenticated, but not allowed. → 403 (the filter produced no context for 401). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource",
                request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return build(resolved, resolved.name(), ex.getReason(), request, List.of());
    }

    /**
     * Genuine bug or unexpected failure.
     *
     * <p>The full stack trace goes to the log; the client gets a generic
     * message. That split is the standard practice — internal detail helps you
     * debug, but it also leaks table names, file paths and library versions to
     * anyone who triggers it.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again.", request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request, List<String> details) {
        return ResponseEntity.status(status).body(ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                code,
                message == null ? status.getReasonPhrase() : message,
                request.getRequestURI(),
                details));
    }
}
