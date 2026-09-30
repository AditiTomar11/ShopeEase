package com.aditi.product_service.infrastructure.config;

import com.aditi.product_service.domain.port.FileStorage;
import com.aditi.product_service.infrastructure.storage.LocalFileStorage;
import com.aditi.product_service.infrastructure.storage.S3FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Chooses which {@link FileStorage} implementation the container will inject.
 *
 * <h2>This one class is the whole "Dependency Injection" topic</h2>
 * {@code ImageService} declares a dependency on the {@code FileStorage}
 * <em>interface</em>. It has no {@code if (useS3)} anywhere. Which
 * implementation arrives is decided once, here, at start-up, by a property. The
 * application layer's bytecode is identical in every environment.
 *
 * <p>Three ways to make that choice appear, and why this one is used:
 * <ul>
 *   <li><b>field injection + {@code @Qualifier}</b> — works, but the choice is
 *       invisible at the point of use and a missing bean only fails at runtime
 *       when that exact code path runs.</li>
 *   <li><b>two {@code @Service} classes guarded by {@code @Profile}</b> — the
 *       old Spring Boot 1 way; profiles leak into your classes and you end up
 *       with several profile annotations on one class.</li>
 *   <li><b>{@code @ConditionalOnProperty} on a {@code @Bean} method</b> — used
 *       here. The condition is evaluated once during context startup, the
 *       decision lives in one readable place, and exactly one bean of type
 *       {@code FileStorage} exists. Had both matched, injection would fail with
 *       {@code NoUniqueBeanDefinitionException} at start-up rather than
 *       mysteriously picking one at runtime.</li>
 * </ul>
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    /**
     * Local-disk backend, active unless {@code app.storage.type=s3}.
     *
     * <p>Two conditions rather than one: "the type is local <em>or</em> nothing
     * was specified". A typo such as {@code type=amazon} therefore falls back to
     * local with a warning rather than producing a context with no
     * {@code FileStorage} bean and failing to start.
     */
    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorage localFileStorage(
            @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}") String uploadDir,
            @org.springframework.beans.factory.annotation.Value("${app.upload.public-base-url:/uploads/}") String publicBaseUrl) {

        Path base = Paths.get(uploadDir);
        log.info("app.storage.type=local → storing product images on disk (development only)");
        return new LocalFileStorage(base, publicBaseUrl);
    }

    /**
     * S3 backend, active only with {@code app.storage.type=s3}.
     *
     * <p>Note the return type is the port, not {@code S3FileStorage}. Everything
     * above this method therefore stays coupled to the abstraction.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public FileStorage s3FileStorage(StorageProperties properties) {
        log.info("app.storage.type=s3 → bucket={} region={}",
                properties.bucket(), properties.region());
        return S3FileStorage.create(
                properties.region(),
                properties.bucket(),
                properties.publicBaseUrl(),
                properties.accessKey(),
                properties.secretKey(),
                String.valueOf(properties.pathStyleAccess()));
    }

    /**
     * Serves locally-stored images over HTTP.
     *
     * <p>Only active for the local backend: when images live in S3 the browser
     * fetches them from S3 and this handler would just be dead weight.
     */
    @Configuration
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public static class LocalStaticResourceConfig implements WebMvcConfigurer {

        private static final String UPLOADS_PATTERN = "/uploads/**";

        private final String uploadDir;

        LocalStaticResourceConfig(
                @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}") String uploadDir) {
            this.uploadDir = uploadDir;
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            // Built from the SAME property the storage bean uses, rather than a
            // hard-coded "file:./uploads/". A hard-coded location here is a real
            // bug: change app.upload.dir and the uploads still work while every
            // image 404s, which is exactly the kind of inconsistency that costs
            // an afternoon.
            //
            // The trailing slash matters: without it Spring treats the last
            // segment as a file name instead of a directory.
            String location = "file:" + Paths.get(uploadDir).toAbsolutePath().normalize() + "/";

            registry.addResourceHandler(UPLOADS_PATTERN)
                    .addResourceLocations(location)
                    // One day: filenames contain a random UUID and never change,
                    // so a cached copy is always correct.
                    .setCachePeriod(86_400);
        }
    }
}
