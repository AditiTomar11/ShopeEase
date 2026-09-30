package com.aditi.product_service.domain.exception;

/** No file part, or a zero-byte file. Mapped to 400. */
public class EmptyUploadException extends ProductDomainException {

    public EmptyUploadException() {
        super("EMPTY_UPLOAD", "No file was supplied in the request");
    }
}
