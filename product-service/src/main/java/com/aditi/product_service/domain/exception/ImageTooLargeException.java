package com.aditi.product_service.domain.exception;

/** The upload exceeds the configured limit. Mapped to 413. */
public class ImageTooLargeException extends ProductDomainException {

    public ImageTooLargeException(long actualBytes, long maxBytes) {
        super("IMAGE_TOO_LARGE",
                "Image is " + (actualBytes / 1024) + " KB but the limit is " + (maxBytes / 1024) + " KB");
    }
}
