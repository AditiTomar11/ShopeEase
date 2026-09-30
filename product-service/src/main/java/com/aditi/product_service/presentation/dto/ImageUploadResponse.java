package com.aditi.product_service.presentation.dto;

import com.aditi.product_service.domain.model.StoredFile;

/**
 * The JSON body returned after a successful upload.
 *
 * <p>{@code key} is included so the client can reference the object later
 * (for example to delete it) without parsing it back out of the URL. {@code url}
 * is what the admin form drops into the product's {@code imageUrl}.
 */
public record ImageUploadResponse(String key, String url, String contentType, long sizeBytes) {

    public static ImageUploadResponse from(StoredFile stored) {
        return new ImageUploadResponse(
                stored.key(), stored.url(), stored.contentType(), stored.sizeBytes());
    }
}
