package com.aditi.product_service.application.service;

import com.aditi.product_service.domain.exception.EmptyUploadException;
import com.aditi.product_service.domain.exception.ImageTooLargeException;
import com.aditi.product_service.domain.exception.UnsupportedImageTypeException;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;
import com.aditi.product_service.domain.port.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * APPLICATION LAYER — the "accept a product image" use case.
 *
 * <h2>Where the rules live and why</h2>
 * <ul>
 *   <li><b>Whitelisting the content type, and the extension map</b> — here,
 *       because "which types are we willing to publish?" is a business policy,
 *       not a transport concern.</li>
 *   <li><b>The size limit</b> — configuration, injected here and passed to the
 *       domain, so the same code can enforce 5 MB locally and 50 MB in
 *       production without a recompile.</li>
 *   <li><b>Reading the bytes off the multipart request</b> — the controller's
 *       job, and it hands over a plain {@link UploadRequest}.</li>
 * </ul>
 *
 * <p>Notice the two-step defence on the filename: the extension is
 * <b>derived from the declared content type</b>, never from what the client
 * called the file. A request called {@code evil.html} with
 * {@code Content-Type: image/png} is stored as a random {@code .png} and served
 * with a browser-sniffing-proof content type. Trusting the filename is the
 * single most common mistake in upload handlers.
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    /**
     * The only types we will publish. SVG is absent on purpose: it is XML that
     * can carry inline script, so serving one from the site's own origin is
     * stored XSS.
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif");

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/avif", "avif");

    private static final String KEY_PREFIX = "products/";

    private final FileStorage fileStorage;
    private final long maxBytes;

    public ImageService(FileStorage fileStorage,
                        @Value("${app.upload.max-size-bytes:5242880}") long maxBytes) {
        this.fileStorage = fileStorage;
        this.maxBytes = maxBytes;
    }

    public StoredFile store(UploadRequest upload) {
        validate(upload);

        String key = KEY_PREFIX + UUID.randomUUID() + "."
                + EXTENSION_BY_TYPE.get(upload.contentType());

        StoredFile stored = fileStorage.store(key, upload);

        log.info("Stored image key={} contentType={} sizeBytes={} backend={}",
                stored.key(), stored.contentType(), stored.sizeBytes(), fileStorage.describe());

        return stored;
    }

    private void validate(UploadRequest upload) {
        if (upload == null || upload.content() == null || upload.sizeBytes() == 0) {
            throw new EmptyUploadException();
        }
        if (upload.sizeBytes() > maxBytes) {
            throw new ImageTooLargeException(upload.sizeBytes(), maxBytes);
        }

        // Browsers send "image/png"; some clients and proxies uppercase or add
        // parameters ("image/png; charset=binary"), so normalise before comparing.
        String contentType = upload.contentType() == null
                ? ""
                : upload.contentType().split(";")[0].trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new UnsupportedImageTypeException(
                    upload.contentType(), String.join(", ", EXTENSION_BY_TYPE.keySet()));
        }
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
