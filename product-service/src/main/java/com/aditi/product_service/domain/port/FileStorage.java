package com.aditi.product_service.domain.port;

import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;

/**
 * Outbound port for binary storage — the seam that lets the same use cases run
 * against S3 in production and the local disk in development.
 *
 * <h2>Why this is the single most valuable interface in the service</h2>
 * Product images are the one place where "which cloud vendor are we on?" would
 * otherwise leak all the way into the application layer. With this port:
 * <ul>
 *   <li>{@code ImageService} calls {@code store(...)} and has no idea whether
 *       the bytes travel over HTTPS to AWS or hit {@code ./uploads};</li>
 *   <li>swapping S3 for GCS/Cloudflare R2/minio is a new class plus a property;</li>
 *   <li>tests need no cloud account at all;</li>
 *   <li>and the app still runs on a laptop with no AWS credentials.</li>
 * </ul>
 *
 * <p>Two implementations ship with the project:
 * {@code infrastructure.storage.S3FileStorage} (the real one) and
 * {@code infrastructure.storage.LocalFileStorage} (the fallback), selected by
 * {@code app.storage.type}. Selection is a DI concern and lives in
 * {@code StorageConfig}.
 */
public interface FileStorage {

    /**
     * Persists the bytes and returns where they ended up.
     *
     * @throws StorageException if the backend rejects or fails the write
     */
    StoredFile store(String key, UploadRequest upload);

    /**
     * Removes an object. Best-effort by contract: a missing object is not an
     * error, because the caller's real goal (the product record is gone) is
     * already achieved.
     */
    void delete(String key);

    /** The publicly reachable URL for a key. */
    String urlFor(String key);

    /**
     * Recovers the storage key from a URL we previously handed out, so a stored
     * {@code imageUrl} can be deleted later.
     *
     * <p>Implemented as a default method on the port because the answer is the
     * same regardless of backend: the key is whatever follows the base URL.
     * Backends that use a different URL shape override it.
     *
     * @return the key, or {@code null} when the URL was not produced by us
     *         (e.g. an admin pasted an external image link — there is nothing of
     *         ours to delete).
     */
    default String keyFromUrl(String url) {
        String base = urlFor("");
        if (base == null || !url.startsWith(base)) {
            return null;
        }
        String key = url.substring(base.length());
        return key.isBlank() ? null : key;
    }

    /** Short name of the active backend, surfaced on /health for debugging. */
    String describe();

    /**
     * Whether this backend can hand out a URL the client can upload to
     * <em>directly</em>, bypassing this service entirely.
     *
     * <p>On the port rather than detected with {@code instanceof} so that the
     * question is asked of an abstraction instead of answered by the controller
     * knowing about concrete classes. A backend added later gets a sensible
     * "no" for free.
     */
    default boolean supportsDirectUpload() {
        return false;
    }

    /**
     * Produces a temporary, signed URL the client can {@code PUT} bytes to.
     *
     * <p>Default implementation refuses, so every backend that cannot do this
     * does not have to override anything.
     *
     * @throws StorageException on any backend without presigning support
     */
    default String presignPutUrl(String key, String contentType, java.time.Duration validity) {
        throw new StorageException(
                "Direct upload is not supported by the " + describe() + " backend. "
                        + "Use the multipart upload endpoint instead.");
    }
}
