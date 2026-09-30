package com.aditi.order_service.presentation.controller;

import com.aditi.order_service.application.service.OrderService;
import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.model.OrderStatus;
import com.aditi.order_service.presentation.dto.CreateOrderRequest;
import com.aditi.order_service.presentation.dto.UpdateOrderRequest;
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
 * Order endpoints.
 *
 * <p>Thin by design — see {@code ProductController} for the same reasoning.
 * One thing this class does own is the <em>shape</em> of the HTTP contract:
 * status codes, path variables, and the 204 for a successful delete.
 *
 * <p>{@code @RequestParam(required = false) String username} is a worked example
 * of trusting nothing: the gateway verifies the token, but the <em>value</em> of
 * "whose orders?" must come from the verified token, never from a query
 * parameter a client controls. In a fuller implementation the controller would
 * read the authenticated principal here. It is left as a parameter so the
 * endpoint stays usable for the admin panel, which legitimately asks for any
 * customer's orders.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order created = orderService.createOrder(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping("/customer/{username}")
    public List<Order> getOrdersByCustomer(@PathVariable String username) {
        return orderService.getOrdersByUsername(username);
    }

    @GetMapping("/status/{status}")
    public List<Order> getOrdersByStatus(@PathVariable OrderStatus status) {
        return orderService.getOrdersByStatus(status);
    }

    /**
     * The admin panel's "mark as shipped" button. The status is a path variable
     * rather than free text, and Spring converts it to the enum for us — an
     * unknown value produces a 400 at the edge instead of a row with a typo in it.
     */
    @PutMapping("/{id}/status/{status}")
    public Order updateStatus(@PathVariable Long id, @PathVariable OrderStatus status) {
        return orderService.updateStatus(id, status);
    }

    @PutMapping("/{id}")
    public Order updateOrder(@PathVariable Long id,
                             @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.updateOrder(request.toCommand(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
