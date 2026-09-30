package com.aditi.product_service.application.service;

import com.aditi.product_service.domain.exception.ProductNotFoundException;
import com.aditi.product_service.domain.model.Product;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;
import com.aditi.product_service.domain.port.FileStorage;
import com.aditi.product_service.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the product use cases, including the delete-cleans-up-its-image rule. */
class ProductServiceTest {

    private static final class InMemoryProductRepository implements ProductRepository {
        private final List<Product> rows = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public Product save(Product product) {
            Product stored = product.withId(ids.getAndIncrement());
            rows.removeIf(p -> p.getId().equals(stored.getId()));
            rows.add(stored);
            return stored;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return rows.stream().filter(p -> p.getId().equals(id)).findFirst();
        }

        @Override
        public List<Product> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public Product update(Long id, Product product) {
            if (findById(id).isEmpty()) {
                throw new ProductNotFoundException(id);
            }
            return save(product.withId(id));
        }

        @Override
        public void deleteById(Long id) {
            rows.removeIf(p -> p.getId().equals(id));
        }

        @Override
        public List<Product> findByCategory(String category) {
            return rows.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))
                    .toList();
        }
    }

    private static final class TrackingFileStorage implements FileStorage {
        private final List<String> stored = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private boolean failOnDelete;

        @Override
        public StoredFile store(String key, UploadRequest upload) {
            stored.add(key);
            return new StoredFile(key, "https://cdn.example.com/" + key, upload.contentType(), upload.sizeBytes());
        }

        @Override
        public void delete(String key) {
            if (failOnDelete) {
                throw new IllegalStateException("storage is down");
            }
            deleted.add(key);
        }

        @Override
        public String urlFor(String key) {
            return "https://cdn.example.com/" + key;
        }

        @Override
        public String describe() {
            return "tracking";
        }
    }

    private InMemoryProductRepository repository;
    private TrackingFileStorage storage;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductRepository();
        storage = new TrackingFileStorage();
        service = new ProductService(repository, storage);
    }

    @Test
    @DisplayName("creates a product and assigns it an id")
    void creates() {
        Product created = service.createProduct(Product.create("Phone", 9999.0, null, "Mobile", "desc"));

        assertTrue(created.getId() > 0);
        assertEquals("Phone", created.getName());
    }

    @Test
    @DisplayName("throws ProductNotFoundException for an unknown id")
    void throwsForUnknownId() {
        assertThrows(ProductNotFoundException.class, () -> service.getProduct(404L));
    }

    @Test
    @DisplayName("deleting a product also deletes its stored image")
    void deleteRemovesImage() {
        Product created = service.createProduct(
                Product.create("Phone", 9999.0, "https://cdn.example.com/products/abc.png", "Mobile", null));

        service.deleteProduct(created.getId());

        assertTrue(storage.deleted.contains("products/abc.png"));
        assertNull(repository.findById(created.getId()).orElse(null));
    }

    @Test
    @DisplayName("a storage outage does NOT roll back the product deletion")
    void deleteSurvivesStorageOutage() {
        // The row must go even if the cleanup fails, otherwise an S3 outage
        // would make the whole catalogue undeletable.
        Product created = service.createProduct(
                Product.create("Phone", 9999.0, "https://cdn.example.com/products/abc.png", "Mobile", null));
        storage.failOnDelete = true;

        service.deleteProduct(created.getId());

        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    @DisplayName("ignores an externally hosted imageUrl when deleting")
    void ignoresForeignImageUrl() {
        // An admin may paste an image from anywhere; there is nothing of ours
        // to delete, and we must not issue a delete for a key we never wrote.
        Product created = service.createProduct(
                Product.create("Phone", 9999.0, "https://images.unsplash.com/photo.jpg", "Mobile", null));

        service.deleteProduct(created.getId());

        assertTrue(storage.deleted.isEmpty());
    }

    @Test
    @DisplayName("update keeps the id and re-validates the incoming fields")
    void updateKeepsId() {
        Product created = service.createProduct(Product.create("Old", 10.0, null, "Cat", null));

        Product updated = service.updateProduct(created.getId(),
                Product.create("New", 20.0, null, "Cat2", "d"));

        assertEquals(created.getId(), updated.getId());
        assertEquals("New", updated.getName());
        assertEquals(20.0, updated.getPrice());
    }

    @Test
    @DisplayName("update keeps the existing imageUrl when the request omits one")
    void updatePreservesImage() {
        Product created = service.createProduct(
                Product.create("Old", 10.0, "https://cdn.example.com/products/x.png", "Cat", null));

        Product updated = service.updateProduct(created.getId(), Product.create("New", 20.0, null, "Cat", null));

        assertEquals("https://cdn.example.com/products/x.png", updated.getImageUrl());
    }

    @Test
    @DisplayName("filters by category through the repository, not in memory")
    void filtersByCategory() {
        service.createProduct(Product.create("A", 1.0, null, "Mobile", null));
        service.createProduct(Product.create("B", 2.0, null, "Laptop", null));
        service.createProduct(Product.create("C", 3.0, null, "mobile", null));

        assertEquals(2, service.getProductsByCategory("Mobile").size());
    }

    @Test
    @DisplayName("attaching a stored image updates only the imageUrl")
    void attachesImage() {
        Product created = service.createProduct(Product.create("A", 1.0, null, "Cat", "keep me"));

        Product updated = service.attachImage(created.getId(),
                new StoredFile("products/new.png", "https://cdn.example.com/products/new.png", "image/png", 10));

        assertEquals("https://cdn.example.com/products/new.png", updated.getImageUrl());
        assertEquals("keep me", updated.getDescription());
    }
}
