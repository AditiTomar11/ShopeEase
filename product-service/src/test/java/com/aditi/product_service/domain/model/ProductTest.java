package com.aditi.product_service.domain.model;

import com.aditi.product_service.domain.exception.InvalidProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the aggregate's invariants.
 *
 * <p>These rules are enforced in the constructor, so they hold for every caller
 * — REST, Feign, a future Kafka consumer, or a test. That is the whole point of
 * putting them here rather than only as annotations on the request DTO.
 */
class ProductTest {

    @Test
    @DisplayName("requires a name")
    void requiresName() {
        assertThrows(InvalidProductException.class, () -> Product.create(null, 10.0, null, null, null));
        assertThrows(InvalidProductException.class, () -> Product.create("   ", 10.0, null, null, null));
    }

    @Test
    @DisplayName("requires a price")
    void requiresPrice() {
        assertThrows(InvalidProductException.class, () -> Product.create("A", null, null, null, null));
    }

    @Test
    @DisplayName("rejects a negative price")
    void rejectsNegativePrice() {
        assertThrows(InvalidProductException.class, () -> Product.create("A", -1.0, null, null, null));
    }

    @Test
    @DisplayName("rejects NaN and Infinity")
    void rejectsNonFinitePrice() {
        assertThrows(InvalidProductException.class, () -> Product.create("A", Double.NaN, null, null, null));
        assertThrows(InvalidProductException.class,
                () -> Product.create("A", Double.POSITIVE_INFINITY, null, null, null));
    }

    @Test
    @DisplayName("rounds the price to two decimals instead of storing float noise")
    void roundsPriceToCents() {
        // Binary floating point cannot represent 19.99 exactly. Storing the raw
        // double would leave 19.989999999999998 in a column people read as money.
        assertEquals(20.0, Product.create("A", 19.999, null, null, null).getPrice());
        assertEquals(0.3, Product.create("A", 0.1 + 0.2, null, null, null).getPrice());
        assertEquals(19.99, Product.create("A", 19.99, null, null, null).getPrice());
    }

    @Test
    @DisplayName("trims whitespace and normalises nulls")
    void trimsAndNormalises() {
        Product p = Product.create("  iPhone  ", 1.0, "  https://x/y.png  ", "  Mobile  ", "  nice  ");

        assertEquals("iPhone", p.getName());
        assertEquals("https://x/y.png", p.getImageUrl());
        assertEquals("Mobile", p.getCategory());
        assertEquals("nice", p.getDescription());
    }

    @Test
    @DisplayName("truncates over-long text instead of failing the whole request")
    void truncatesOverlongText() {
        String tooLong = "x".repeat(500);

        Product p = Product.create(tooLong, 1.0, null, tooLong, tooLong);

        assertEquals(200, p.getName().length());
        assertEquals(100, p.getCategory().length());
        assertEquals(4000, p.getDescription().length());
    }

    @Test
    @DisplayName("is immutable — withX returns a new instance")
    void isImmutable() {
        Product original = Product.create("A", 1.0, null, "Cat", null);
        Product changed = original.withImageUrl("https://x/y.png");

        assertNull(original.getImageUrl());
        assertEquals("https://x/y.png", changed.getImageUrl());
    }

    @Test
    @DisplayName("withId attaches a storage identity without mutating the original")
    void withIdIsNonMutating() {
        Product original = Product.create("A", 1.0, null, null, null);

        assertNull(original.getId());
        assertEquals(7L, original.withId(7L).getId());
    }
}
