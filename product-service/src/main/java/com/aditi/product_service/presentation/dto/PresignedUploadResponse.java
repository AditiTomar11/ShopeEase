package com.aditi.product_service.presentation.dto;

/**
 * Instructions for a <b>direct browser → S3</b> upload.
 *
 * <p>When the backend is S3 this is the production path: the browser receives
 * this object, issues a {@code PUT} of the raw file bytes to {@code uploadUrl}
 * with the two headers listed in {@code requiredHeaders}, then saves the
 * product with {@code publicUrl} as its {@code imageUrl}.
 *
 * <p>The image bytes never touch the API service.
 */
public record PresignedUploadResponse(
        String key,
        String uploadUrl,
        String publicUrl,
        String method,
        java.util.Map<String, String> requiredHeaders,
        long expiresInSeconds) {
}
