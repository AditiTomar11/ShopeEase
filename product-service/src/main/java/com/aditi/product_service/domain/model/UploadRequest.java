package com.aditi.product_service.domain.model;

/**
 * A file handed to the storage port, stripped down to what storage actually
 * needs: bytes, a content type and the client's filename.
 *
 * <p>Keeping the raw {@code MultipartFile} out of this type is the whole point
 * of the port: if the domain knew about {@code MultipartFile}, replacing the
 * HTTP upload endpoint with a message-queue or S3-event trigger would require
 * touching the domain. It only ever sees "here are some bytes".
 */
public record UploadRequest(String originalFilename, String contentType, byte[] content) {

    public int sizeBytes() {
        return content == null ? 0 : content.length;
    }
}
