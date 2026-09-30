package com.aditi.product_service.presentation.controller;

import com.aditi.product_service.application.service.ProductService;
import com.aditi.product_service.domain.model.Product;
import com.aditi.product_service.presentation.dto.ProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Product CRUD over HTTP.
 *
 * <p>Read this class as the demonstration of how thin a controller should be.
 * Every method is a two-liner: translate, delegate, return. There is no
 * business rule, no {@code if}, no try/catch — failures are thrown as domain
 * exceptions and translated to status codes by
 * {@link com.aditi.product_service.presentation.exception.GlobalExceptionHandler}.
 *
 * <p>That thinness is the practical payoff of the layering. Compare with a
 * design where this class owned the rules: the same rules would be missing from
 * the Kafka consumer, unreachable from the gRPC service, and impossible to unit
 * test without standing up MockMvc.
 *
 * <p>Endpoints are public; the API gateway is what enforces that only an ADMIN
 * may call the mutating ones. See the gateway's security rules.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.createProduct(request.toDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Fetches one product, or 404 via {@code ProductNotFoundException}.
     */
    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/category/{category}")
    public List<Product> getByCategory(@PathVariable String category) {
        return productService.getProductsByCategory(category);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id,
                                 @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request.toDomain());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        // 204 No Content: the request succeeded and there is deliberately no
        // body. Returning 200 with null is the most common REST mistake here.
        return ResponseEntity.noContent().build();
    }
}
