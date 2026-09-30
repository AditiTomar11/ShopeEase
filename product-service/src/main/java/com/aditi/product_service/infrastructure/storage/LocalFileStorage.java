package com.aditi.product_service.infrastructure.storage;

import com.aditi.product_service.domain.exception.StorageException;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;
import com.aditi.product_service.domain.port.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Filesystem implementation of {@link FileStorage}.
 *
 * <h2>Why it exists</h2>
 * Three reasons, and the third is the important one:
 * <ol>
 *   <li>so the project runs on a laptop with no AWS account at all;</li>
 *   <li>so tests and CI never touch a cloud bucket;</li>
 *   <li>as the proof that the {@code FileStorage} port is genuinely an
 *       abstraction — {@link com.aditi.product_service.application.service.ImageService}
 *       contains no reference to S3 and behaves identically against either backend.</li>
 * </ol>
 *
 * <h2>It is not a production backend</h2>
 * The container filesystem is ephemeral: every new deploy, and every scale-up,
 * starts from a clean image and loses the uploaded files. Local disk inside a
 * container is not a place to keep user data — that lesson (containers are
 * disposable, state goes outside) is the foundation of the whole twelve-factor
 * argument. {@code app.storage.type=local} is a development default only.
 *
 * <h2>Path traversal</h2>
 * The key is validated against the base directory before anything is written.
 * Keys arrive from {@code ImageService}, which generates them as
 * {@code products/<uuid>.<ext>}, but defence in depth is cheap here and the
 * alternative — a key of {@code ../../etc/passwd} — is the classic upload bug.
 */
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalFileStorage(Path baseDir, String publicBaseUrl) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        // Normalise once here so keyFromUrl can do a plain startsWith comparison
        // and callers never have to remember the trailing slash.
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new StorageException("Could not create upload directory " + this.baseDir, e);
        }
        log.info("Local file storage active: dir={} publicBaseUrl={}", this.baseDir, this.publicBaseUrl);
    }

    @Override
    public StoredFile store(String key, UploadRequest upload) {
        Path target = resolveSafely(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = new java.io.ByteArrayInputStream(upload.content())) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(key, urlFor(key), upload.contentType(), upload.sizeBytes());
        } catch (IOException e) {
            log.error("Local store failed for key={}: {}", key, e.getMessage(), e);
            throw new StorageException("Could not write the image to disk", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            // Best effort by contract: a missing file is not a failure.
            Files.deleteIfExists(resolveSafely(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete the image from disk", e);
        }
    }

    @Override
    public String urlFor(String key) {
        return publicBaseUrl + key;
    }

    @Override
    public String keyFromUrl(String url) {
        if (url == null || !url.startsWith(publicBaseUrl)) {
            return null;
        }
        String key = url.substring(publicBaseUrl.length());
        return key.isBlank() ? null : key;
    }

    @Override
    public String describe() {
        return "local:" + baseDir;
    }

    /**
     * Resolves a key inside the base directory, rejecting anything that escapes it.
     *
     * <p>{@code normalize()} collapses {@code ..} segments, and the
     * {@code startsWith} check is the real guard: a key like
     * {@code ../../etc/passwd} normalises to a path that no longer sits under
     * the base directory, so it is refused here rather than silently written.
     */
    private Path resolveSafely(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new StorageException("Rejected key outside the upload directory: " + key);
        }
        return resolved;
    }
}
