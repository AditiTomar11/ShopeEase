package com.aditi.product_service.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Type-safe binding for the {@code app.storage.*} block.
 *
 * <p>This is the switch that decides whether product images go to AWS S3 or to
 * the local disk. One property, no code change — which is the practical payoff
 * of having put the decision behind the {@code FileStorage} port.
 *
 * <p>All AWS values are optional on purpose. With {@code type: local} the
 * service starts with no AWS configuration whatsoever, and
 * {@code type: s3} works on a laptop using the shared ~/.aws/credentials file.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(

        /** {@code local} (default) or {@code s3}. */
        @DefaultValue("local") String type,

        @DefaultValue("shopease-product-images") String bucket,

        /** e.g. {@code ap-south-1}. */
        @DefaultValue("ap-south-1") String region,

        /**
         * Base URL that {@code imageUrl} is built from. Usually the bucket's
         * public endpoint or a CloudFront distribution in front of it.
         */
        @DefaultValue("https://shopease-product-images.s3.ap-south-1.amazonaws.com/") String publicBaseUrl,

        /** Leave blank to use the default credentials chain (the production path). */
        String accessKey,

        String secretKey,

        /** Required by MinIO and other S3-compatible stores; leave false for AWS. */
        @DefaultValue("false") boolean pathStyleAccess) {
}
