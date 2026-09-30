package com.aditi.product_service.domain.exception;

/**
 * The storage backend refused or failed to complete an operation.
 *
 * <p>Deliberately carries the vendor detail only in the message for the log;
 * the client-facing body is sanitised by the exception handler, because an S3
 * error can contain a bucket name, an account id and a request key.
 */
public class StorageException extends ProductDomainException {

    public StorageException(String message) {
        super("STORAGE_FAILURE", message);
    }

    public StorageException(String message, Throwable cause) {
        super("STORAGE_FAILURE", message);
        initCause(cause);
    }
}
