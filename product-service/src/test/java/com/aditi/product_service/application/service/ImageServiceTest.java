package com.aditi.product_service.application.service;

import com.aditi.product_service.domain.exception.EmptyUploadException;
import com.aditi.product_service.domain.exception.ImageTooLargeException;
import com.aditi.product_service.domain.exception.StorageException;
import com.aditi.product_service.domain.exception.UnsupportedImageTypeException;
import com.aditi.product_service.domain.model.StoredFile;
import com.aditi.product_service.domain.model.UploadRequest;
import com.aditi.product_service.domain.port.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the upload use case — the security rules of image upload.
 *
 * <p>The fake {@link FileStorage} below is the proof that the port is a real
 * abstraction: this entire suite runs with no AWS account, no network and no
 * container.
 */
class ImageServiceTest {

    /** Records what it was asked to store, and never touches a disk. */
    private static class FakeFileStorage implements FileStorage {
        private final List<String> keys = new ArrayList<>();
        private final List<byte[]> bodies = new ArrayList<>();
        private String base = "https://cdn.example.com/";

        @Override
        public StoredFile store(String key, UploadRequest upload) {
            keys.add(key);
            bodies.add(upload.content());
            return new StoredFile(key, base + key, upload.contentType(), upload.sizeBytes());
        }

        @Override
        public void delete(String key) {
            keys.remove(key);
        }

        @Override
        public String urlFor(String key) {
            return base + key;
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    private static final long MAX = 1024L * 1024L; // 1 MB in the test

    private FakeFileStorage storage;
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        storage = new FakeFileStorage();
        imageService = new ImageService(storage, MAX);
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        java.util.Arrays.fill(b, (byte) 'a');
        return b;
    }

    @Test
    @DisplayName("stores a valid image and returns its URL")
    void storesValidImage() {
        StoredFile stored = imageService.store(
                new UploadRequest("phone.png", "image/png", bytes(1024)));

        assertTrue(stored.url().startsWith("https://cdn.example.com/products/"));
        assertEquals("image/png", stored.contentType());
        assertEquals(1024, stored.sizeBytes());
    }

    @Test
    @DisplayName("derives the extension from the content type, not the filename")
    void ignoresClientFilenameForExtension() {
        // The classic upload vulnerability: a file called "evil.html" or
        // "../../etc/passwd" must never influence the stored object name.
        StoredFile stored = imageService.store(
                new UploadRequest("../../evil.html", "image/png", bytes(10)));

        assertTrue(stored.key().endsWith(".png"), "key was: " + stored.key());
        assertTrue(stored.key().startsWith("products/"));
        assertTrue(!stored.key().contains(".."));
        assertTrue(!stored.key().contains("evil"));
    }

    @Test
    @DisplayName("gives every upload a unique key, so no file overwrites another")
    void generatesUniqueKeys() {
        imageService.store(new UploadRequest("a.png", "image/png", bytes(10)));
        imageService.store(new UploadRequest("a.png", "image/png", bytes(10)));

        assertEquals(2, storage.keys.size());
        assertNotEquals(storage.keys.get(0), storage.keys.get(1));
    }

    @Test
    @DisplayName("rejects an empty upload")
    void rejectsEmpty() {
        assertThrows(EmptyUploadException.class,
                () -> imageService.store(new UploadRequest(null, "image/png", new byte[0])));
        assertThrows(EmptyUploadException.class,
                () -> imageService.store(new UploadRequest(null, null, null)));
    }

    @Test
    @DisplayName("rejects a file over the configured limit")
    void rejectsOversize() {
        assertThrows(ImageTooLargeException.class,
                () -> imageService.store(new UploadRequest("big.png", "image/png", bytes((int) MAX + 1))));
    }

    @Test
    @DisplayName("rejects anything that is not an allowed image type")
    void rejectsNonImages() {
        // Rejecting by whitelist, not blacklist: a .html or .svg served from our
        // own origin is stored XSS.
        assertThrows(UnsupportedImageTypeException.class,
                () -> imageService.store(new UploadRequest("x.html", "text/html", bytes(10))));
        assertThrows(UnsupportedImageTypeException.class,
                () -> imageService.store(new UploadRequest("x.svg", "image/svg+xml", bytes(10))));
        assertThrows(UnsupportedImageTypeException.class,
                () -> imageService.store(new UploadRequest("x.exe", "application/octet-stream", bytes(10))));
        assertThrows(UnsupportedImageTypeException.class,
                () -> imageService.store(new UploadRequest("x", null, bytes(10))));
    }

    @Test
    @DisplayName("tolerates a content type carrying parameters, and odd casing")
    void normalisesContentType() {
        // Some proxies append "; charset=binary" and some clients uppercase it.
        assertEquals(".jpg", extensionOf("IMAGE/JPEG"));
        assertEquals(".png", extensionOf("image/png; charset=binary"));
    }

    @Test
    @DisplayName("propagates a backend failure as a StorageException")
    void propagatesBackendFailure() {
        FileStorage broken = new FakeFileStorage() {
            @Override
            public StoredFile store(String key, UploadRequest upload) {
                throw new StorageException("bucket not found");
            }
        };

        StorageException thrown = assertThrows(StorageException.class,
                () -> new ImageService(broken, MAX).store(
                        new UploadRequest("a.png", "image/png", bytes(10))));

        assertEquals("STORAGE_FAILURE", thrown.getCode());
    }

    private String extensionOf(String contentType) {
        StoredFile stored = imageService.store(new UploadRequest("x", contentType, bytes(10)));
        return stored.key().substring(stored.key().lastIndexOf('.'));
    }
}
