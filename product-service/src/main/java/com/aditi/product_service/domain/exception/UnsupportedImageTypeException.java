package com.aditi.product_service.domain.exception;

/**
 * The uploaded file is not an allowed image type. Mapped to 415.
 *
 * <p>Whitelisting types (rather than blacklisting bad ones) is deliberate. A
 * blacklist is always one extension behind an attacker, and "an uploaded
 * .html or .svg" is a stored-XSS vector: the browser would render it from the
 * same origin and run script in the site's context.
 */
public class UnsupportedImageTypeException extends ProductDomainException {

    private final String received;

    public UnsupportedImageTypeException(String received, String allowed) {
        super("UNSUPPORTED_MEDIA_TYPE",
                "Unsupported image type '" + received + "'. Allowed: " + allowed);
        this.received = received;
    }

    public String getReceived() {
        return received;
    }
}
