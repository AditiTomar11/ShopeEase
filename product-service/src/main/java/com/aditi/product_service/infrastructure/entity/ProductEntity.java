package com.aditi.product_service.infrastructure.entity;

import com.aditi.product_service.domain.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The JPA entity — the database's view of a product.
 *
 * <p>Everything that makes this class <em>storage-shaped</em> rather than
 * business-shaped lives here: the identity strategy, the column lengths, the
 * fact that it needs a no-arg constructor and mutable setters for Hibernate.
 *
 * <p>The domain {@link Product} has none of that. It is immutable and validates
 * itself, which is exactly what JPA cannot work with — hence two classes and a
 * pair of mapping methods between them. The cost is a few lines of boilerplate;
 * the benefit is that the domain survives a move to MongoDB, and that Hibernate
 * can never call a domain constructor with a half-built object.
 */
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * DOUBLE rather than DECIMAL. Honest trade-off: DOUBLE is what Java's
     * {@code Double} maps to and avoids a conversion, but it cannot represent
     * 0.10 exactly. Prices are validated and rounded to two decimals by the
     * domain, and this is a catalogue display price rather than an accounting
     * ledger. A real payments system would use BigDecimal + NUMERIC(19,4).
     */
    @Column(nullable = false)
    private Double price;

    /** Plain column, not a relation: storing a URL keeps reads to one table. */
    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 100)
    private String category;

    @Column(length = 4000)
    private String description;

    protected ProductEntity() {
        // required by JPA
    }

    public ProductEntity(Long id, String name, Double price, String imageUrl,
                         String category, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.category = category;
        this.description = description;
    }

    /** Persistence-facing → domain-facing. */
    public Product toDomain() {
        return Product.create(name, price, imageUrl, category, description)
                .withId(id);
    }

    /** Domain-facing → persistence-facing. */
    public static ProductEntity from(Product product) {
        return new ProductEntity(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getImageUrl(),
                product.getCategory(),
                product.getDescription());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
