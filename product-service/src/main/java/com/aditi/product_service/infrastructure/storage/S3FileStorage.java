package com.aditi.product_service.infrastructure.storage;

import com.aditi.product_service.domain.exception.StorageException;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;
import com.aditi.product_service.domain.port.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;

/**
 * The real implementation of {@link FileStorage}: Amazon S3.
 *
 * <h2>How S3 actually works</h2>
 * S3 is a flat key/value object store addressed over HTTP. There is no real
 * directory tree — {@code products/8f2c/phone.png} is one key that happens to
 * contain slashes, which is why the console renders it as folders. Uploads are
 * a single {@code PUT} of the raw bytes to a URL built from
 * bucket + region + key.
 *
 * <h2>Why the SDK rather than a hand-rolled HTTPS PUT</h2>
 * {@link software.amazon.awssdk.services.s3.S3Client} adds SigV4 request
 * signing, automatic retries with exponential backoff and jitter, connection
 * pooling, and regional endpoint resolution. SigV4 in particular is a
 * multi-step canonicalisation algorithm (sign the headers, build a string to
 * sign, HMAC it four times, add the signature) — hand-rolling it is exactly
 * the kind of code that works until AWS rotates something.
 *
 * <h2>Credentials</h2>
 * Two paths, in priority order:
 * <ol>
 *   <li><b>Instance / task / container role</b> — when
 *       {@code AWS_ACCESS_KEY_ID} is not set, the
 *       {@link DefaultCredentialsProvider} walks the chain: env vars → shared
 *       profile → ECS task role → EC2 instance metadata. <b>This is the one to
 *       use in production</b>: there is no long-lived key to leak, rotate or
 *       paste into a CI secret.</li>
 *   <li><b>Static keys</b> — used only when {@code app.storage.access-key} is
 *       supplied, for local development against a real bucket.</li>
 * </ol>
 *
 * <h2>What this class deliberately does not do</h2>
 * It does not resize or re-encode images, does not scan for malware, and does
 * not set ACLs per object. Public access comes from the <em>bucket</em>
 * policy, which is the right level for this use case — see
 * {@code docs/INTERVIEW_PREP.md}. A per-object {@code PutObjectAcl} call is
 * both slower and an easy way to accidentally leave a bucket half-public.
 */
public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3FileStorage(S3Client s3Client,
                         S3Presigner presigner,
                         String bucket,
                         String publicBaseUrl) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * Builds the client pair from configuration.
     *
     * <p>Two clients because they do different jobs: {@link S3Client} performs
     * signed data operations, while {@link S3Presigner} produces temporary
     * URLs that let a client upload <em>directly to S3</em> without the bytes
     * ever passing through this service.
     *
     * <p>Both are closed by Spring on shutdown (they implement
     * {@code AutoCloseable} and are declared as beans).
     */
    public static S3FileStorage create(String region,
                                       String bucket,
                                       String publicBaseUrl,
                                       String accessKey,
                                       String secretKey,
                                       String pathStyleAccess) {

        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(resolveCredentials(accessKey, secretKey));

        // Path-style addressing (bucket in the path rather than the hostname)
        // is what S3-compatible services such as MinIO require. Real AWS works
        // with either, but virtual-host style is the default and is preferred.
        if (Boolean.parseBoolean(pathStyleAccess)) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .chunkedEncodingEnabled(false)   // MinIO and some proxies dislike chunked uploads
                    .build());
        }

        S3Client client = builder.build();
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(resolveCredentials(accessKey, secretKey))
                .build();

        return new S3FileStorage(client, presigner, bucket, publicBaseUrl);
    }

    private static software.amazon.awssdk.auth.credentials.AwsCredentialsProvider resolveCredentials(
            String accessKey, String secretKey) {

        if (accessKey == null || accessKey.isBlank()) {
            log.info("No static S3 credentials configured — using the default provider chain "
                    + "(env vars, shared profile, ECS task role, or EC2 instance metadata)");
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Override
    public StoredFile store(String key, UploadRequest upload) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(upload.contentType())
                    .contentLength((long) upload.sizeBytes())
                    // Cache-control is what makes the image fast on the second
                    // view; product images are immutable at a given key because
                    // every upload gets a random UUID.
                    .cacheControl("public, max-age=31536000, immutable")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(upload.content()));

            return new StoredFile(key, publicBaseUrl + key, upload.contentType(), upload.sizeBytes());

        } catch (RuntimeException awsError) {
            // Bucket missing, credentials rejected, region wrong, throttled...
            // The vendor message is valuable in the log and dangerous in a
            // response body, so it is logged here and sanitised by the handler.
            log.error("S3 putObject failed for key={} in bucket={}: {}",
                    key, bucket, awsError.getMessage(), awsError);
            throw new StorageException("Could not store the image in object storage", awsError);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (RuntimeException awsError) {
            log.error("S3 deleteObject failed for key={} in bucket={}: {}",
                    key, bucket, awsError.getMessage(), awsError);
            throw new StorageException("Could not delete the image from object storage", awsError);
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

    /**
     * Produces a temporary URL the browser can {@code PUT} to directly.
     *
     * <h2>Why this is the production pattern</h2>
     * Routing a 5 MB image through the API means: 5 MB up through Render, 5 MB
     * through the JVM, 5 MB into memory as a byte array, then 5 MB back down to
     * S3. On a 0.1-CPU instance that is a request that occupies a worker thread
     * for seconds and can be made to fail by simply repeating it.
     *
     * <p>With a presigned URL the browser uploads straight to S3. The service
     * only hands out a ticket, so the service stays small, the upload is as fast
     * as the user's connection, and the API never holds image bytes at all.
     *
     * <h2>What the signature does</h2>
     * SigV4 signs the request <em>and</em> a validity window (default 15
     * minutes). S3 recomputes the signature using the caller's credentials and
     * rejects the request if it does not match, or if it arrives after expiry.
     * No secret is ever sent to the browser — possessing the URL is the whole
     * authorisation, which is why it must be treated as a credential and never
     * logged.
     */
    public String presignPutUrl(String key, String contentType, Duration validity) {
        try {
            var request = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PresignedPutObjectRequest presigned = presigner.presignPutObject(request);

            // Only the presigner knows the signature; the client just needs the URL.
            return presigned.url().toString();

        } catch (RuntimeException awsError) {
            log.error("Could not presign a PUT URL for key={}: {}", key, awsError.getMessage(), awsError);
            throw new StorageException("Could not create a direct-upload URL", awsError);
        }
    }

    @Override
    public String describe() {
        return "s3://" + bucket;
    }

    /** Closes the underlying HTTP connection pools. Called by Spring on shutdown. */
    public void close() {
        presigner.close();
        s3Client.close();
    }
}
