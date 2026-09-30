package com.aditi.order_service.domain.model;

import java.util.Objects;

/**
 * A read-only projection of a product, owned by THIS service.
 *
 * <p>It is <b>not</b> a shared class with product-service — it is a separate
 * type that happens to carry the same three fields. That duplication is
 * deliberate, and it is the single most important lesson about microservices:
 *
 * <blockquote>
 * Never share a jar between services. A shared DTO couples their release
 * cycles, their database schemas and their team roadmaps; one breaking change
 * then requires a coordinated deploy of services that had no other reason to
 * change. Services collaborate through <b>contracts</b> (HTTP + JSON), and the
 * cost of that contract is that you copy the shape and let the two drift
 * apart on purpose.
 * </blockquote>
 *
 * <p>The alternative — a {@code common-service} module — looks like DRY and is
 * how teams accidentally end up with a distributed monolith: everything
 * deploys together anyway, but now nobody can deploy anything alone.
 */
public class Product {

    private final Long id;
    private final String name;
    private final Double price;

    public Product(Long id, String name, Double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', price=" + price + '}';
    }
}
