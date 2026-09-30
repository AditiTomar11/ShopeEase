package com.aditi.product_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for product-service.
 *
 * <pre>
 *   presentation  → ProductController, ImageController, error handling
 *   application   → ProductService, ImageService
 *   domain        → Product, ports (ProductRepository, FileStorage), exceptions
 *   infrastructure→ JPA, S3 / local storage, composition root, logging
 * </pre>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
