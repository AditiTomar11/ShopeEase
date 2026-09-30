package com.aditi.product_service.domain.model;

/**
 * The result of persisting a binary file.
 *
 * <p>{@code url} is what goes in the database and eventually in an
 * {@code <img src>} — deliberately a URL rather than a blob, so the database
 * stays small and the browser fetches bytes from a CDN/S3 rather than through
 * the API service.
 */
public record StoredFile(String key, String url, String contentType, long sizeBytes) {

    public StoredFile {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
    }
}
