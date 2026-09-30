package com.aditi.product_service.application.service;

import com.aditi.product_service.domain.exception.ProductNotFoundException;
import com.aditi.product_service.domain.model.Product;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.port.FileStorage;
import com.aditi.product_service.domain.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * APPLICATION LAYER — the product use cases.
 *
 * <p>Note what is absent: no {@code ProductEntity}, no {@code @PathVariable},
 * no status codes, no {@code MultipartFile}. The class deals in domain objects
 * and ports, so the same code serves the REST controller today and a Kafka
 * consumer or a gRPC service tomorrow.
 *
 * <p>Collaborators are constructor-injected and held in {@code final} fields:
 * no field injection, no setters, and the object is safe to publish across
 * threads because it can never change after construction.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final FileStorage fileStorage;

    public ProductService(ProductRepository productRepository, FileStorage fileStorage) {
        this.productRepository = productRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional
    public Product createProduct(Product product) {
        Product saved = productRepository.save(product);
        log.info("Created product id={} name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * @throws ProductNotFoundException when the id does not exist — a named
     *         domain exception the presentation layer turns into a 404, rather
     *         than a {@code RuntimeException} that becomes a 500.
     */
    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product lookup failed: id={}", id);
                    return new ProductNotFoundException(id);
                });
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Filtering is pushed down to the repository, so the database returns only
     * the matching rows instead of the service loading the whole catalogue and
     * discarding most of it in Java.
     */
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Transactional
    public Product updateProduct(Long id, Product incoming) {
        // Read-modify-write inside one transaction: keeps the id and reuses the
        // same validated construction path as create.
        Product existing = getProduct(id);
        Product updated = existing.withDetails(
                incoming.getName(),
                incoming.getPrice(),
                incoming.getImageUrl() != null ? incoming.getImageUrl() : existing.getImageUrl(),
                incoming.getCategory(),
                incoming.getDescription());

        Product saved = productRepository.update(id, updated);
        log.info("Updated product id={}", id);
        return saved;
    }

    /**
     * Deletes the row first, then the image.
     *
     * <p>Order matters. If the image delete failed first and the transaction
     * then rolled back, the product would be left pointing at a file that no
     * longer exists. Removing the row first means a failed cleanup leaves an
     * orphaned object in the bucket — invisible, and reclaimable by a lifecycle
     * rule; a product whose image is missing is visible to every customer.
     *
     * <p>The cleanup is also best-effort: an S3 outage must not roll back a
     * delete the user already asked for.
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        productRepository.deleteById(id);

        if (existing.getImageUrl() != null && !existing.getImageUrl().isBlank()) {
            String key = fileStorage.keyFromUrl(existing.getImageUrl());
            if (key != null) {
                try {
                    fileStorage.delete(key);
                    log.info("Deleted image object key={} for product id={}", key, id);
                } catch (RuntimeException storageDown) {
                    log.warn("Product {} deleted but its image could not be removed: {}",
                            id, storageDown.getMessage());
                }
            }
        }

        log.info("Deleted product id={}", id);
    }

    /** Attaches an already-uploaded image to an existing product. */
    @Transactional
    public Product attachImage(Long productId, StoredFile stored) {
        Product updated = getProduct(productId).withImageUrl(stored.url());
        return productRepository.update(productId, updated);
    }
}
