package com.aditi.product_service.presentation.controller;

import com.aditi.product_service.application.service.ImageService;
import com.aditi.product_service.application.service.ProductService;
import com.aditi.product_service.domain.model.Product;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;
import com.aditi.product_service.domain.port.FileStorage;
import com.aditi.product_service.presentation.dto.ImageUploadResponse;
import com.aditi.product_service.presentation.dto.PresignedUploadResponse;
import com.aditi.product_service.presentation.dto.ProductRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * IMAGE UPLOAD endpoints.
 *
 * <h2>{@code multipart/form-data} is not just "the file"</h2>
 * A multipart body is a sequence of parts, each with its own
 * {@code Content-Disposition} and {@code Content-Type}:
 * <pre>
 *   ------boundary
 *   Content-Disposition: form-data; name="file"; filename="phone.png"
 *   Content-Type: image/png
 *
 *   &lt;binary bytes&gt;
 *   ------boundary--
 * </pre>
 * Spring maps that to a {@link MultipartFile}. The browser builds this body
 * automatically when you set {@code enctype="multipart/form-data"} on the form
 * or pass a {@code FormData} object to XHR/fetch.
 *
 * <h2>What this controller does, and deliberately does not do</h2>
 * It reads bytes and hands them to the application layer as a plain
 * {@link UploadRequest}. It performs <b>no</b> validation of its own — no
 * content-type check, no size check, no key generation. Those live in
 * {@link ImageService} and the domain, which means the same rules apply whether
 * the bytes arrived over HTTP, from a message queue, or from a scheduled job.
 */
@RestController
@RequestMapping("/products")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private static final Duration PRESIGN_VALIDITY = Duration.ofMinutes(15);

    private final ImageService imageService;
    private final ProductService productService;
    private final FileStorage fileStorage;

    /**
     * <p>Note the third dependency: the {@link FileStorage} <em>port</em>, not
     * {@code S3FileStorage}. Two reasons.
     *
     * <p><b>Layering.</b> A presentation class that injects a concrete
     * infrastructure class is a leak, even when the behaviour is useful. The
     * capability belongs on the port — {@code supportsDirectUpload()} and
     * {@code presignPutUrl(...)} are both default methods — so the controller
     * asks an abstraction whether direct upload is possible instead of
     * recognising a class.
     *
     * <p><b>Fragility.</b> Resolving {@code S3FileStorage} with an
     * {@code ObjectProvider} only works if Spring can predict the concrete type
     * of a {@code @Bean} method whose declared return type is an interface. It
     * usually can, but it is not something to rely on — and when it cannot, the
     * provider quietly returns {@code null} and the feature silently
     * disappears. Depending on the port has no such failure mode.
     */
    public ImageController(ImageService imageService,
                           ProductService productService,
                           FileStorage fileStorage) {
        this.imageService = imageService;
        this.productService = productService;
        this.fileStorage = fileStorage;
    }

    /**
     * Uploads one image and returns its URL.
     *
     * <p>Returns 201 Created: a new object now exists at a URL the caller did
     * not previously know. The response body is JSON (not a redirect to the
     * file) so the admin form can immediately assign the URL to the product.
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {

        StoredFile stored = imageService.store(toUploadRequest(file));
        return ResponseEntity.status(HttpStatus.CREATED).body(ImageUploadResponse.from(stored));
    }

    /**
     * Uploads an image and creates the product in one request.
     *
     * <p>One call instead of two, which is what a real admin form wants: either
     * the image and the product both exist, or neither does.
     */
    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Product> createProductWithImage(
            @RequestPart("file") MultipartFile file,
            @RequestPart("product") @jakarta.validation.Valid ProductRequest productRequest) {

        StoredFile stored = imageService.store(toUploadRequest(file));

        Product created = productService.createProduct(
                productRequest.toDomain().withImageUrl(stored.url()));

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Uploads an image and attaches it to an existing product.
     */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Product attachImageToProduct(@PathVariable Long id,
                                         @RequestPart("file") MultipartFile file) {
        StoredFile stored = imageService.store(toUploadRequest(file));
        return productService.attachImage(id, stored);
    }

    /**
     * Returns a presigned URL so the browser can upload straight to S3.
     *
     * <p>Only available when the S3 backend is active. With the local backend it
     * returns 409 with an explanation, because there is nothing to presign for —
     * the browser has to go through the API to reach the service's own disk.
     */
    @PostMapping("/images/presign")
    public ResponseEntity<?> presignUpload(
            @RequestParam(defaultValue = "image/png") String contentType,
            @RequestParam(defaultValue = "products") String folder) {

        if (!fileStorage.supportsDirectUpload()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "PRESIGN_UNAVAILABLE",
                    "message", "Direct upload requires app.storage.type=s3. "
                            + "With the local backend, use POST /products/images instead."));
        }

        // Strip anything that is not a safe path character from the folder, so a
        // caller cannot steer the key with "../" or absolute-path syntax.
        String safeFolder = folder.replaceAll("[^a-zA-Z0-9/_-]", "");
        String key = safeFolder + "/" + UUID.randomUUID() + extensionFor(contentType);

        String uploadUrl = fileStorage.presignPutUrl(key, contentType, PRESIGN_VALIDITY);

        return ResponseEntity.ok(new PresignedUploadResponse(
                key,
                uploadUrl,
                fileStorage.urlFor(key),
                "PUT",
                // Content-Type must match what was signed, or S3 rejects the PUT
                // with SignatureDoesNotMatch.
                Map.of("Content-Type", contentType),
                PRESIGN_VALIDITY.toSeconds()));
    }

    /** @return the size limit, so the UI can reject a too-large file before uploading it. */
    @GetMapping("/images/limits")
    public Map<String, Object> uploadLimits() {
        return Map.of(
                "maxBytes", imageService.getMaxBytes(),
                "maxMb", imageService.getMaxBytes() / (1024 * 1024),
                "directUploadAvailable", fileStorage.supportsDirectUpload());
    }

    /**
     * Converts a servlet multipart part into a framework-free upload request.
     *
     * <p>This is the boundary translation: the moment {@code MultipartFile} stops
     * existing. Everything downstream works with a byte array, so swapping
     * multipart for a base64 body or a queue message changes only this method.
     */
    private UploadRequest toUploadRequest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            // Let the domain decide what an empty upload means; the application
            // layer throws EmptyUploadException, which maps to 400.
            return new UploadRequest(null, null, new byte[0]);
        }
        try {
            return new UploadRequest(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes());
        } catch (IOException e) {
            log.error("Could not read uploaded file {}: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new com.aditi.product_service.domain.exception.StorageException(
                    "Could not read the uploaded file", e);
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/avif" -> ".avif";
            default -> ".png";
        };
    }
}
