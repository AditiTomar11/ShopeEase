package com.aditi.order_service.application.service;

import com.aditi.order_service.application.command.CreateOrderCommand;
import com.aditi.order_service.application.command.UpdateOrderCommand;
import com.aditi.order_service.domain.exception.InvalidOrderException;
import com.aditi.order_service.domain.exception.OrderNotFoundException;
import com.aditi.order_service.domain.exception.ProductUnavailableException;
import com.aditi.order_service.domain.model.Order;
import com.aditi.order_service.domain.model.OrderStatus;
import com.aditi.order_service.domain.model.Product;
import com.aditi.order_service.domain.port.ProductCatalog;
import com.aditi.order_service.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the order use cases.
 *
 * <p>The fake {@link ProductCatalog} at the bottom is the whole argument for the
 * {@code ProductCatalog} port: this suite has no Feign client, no HTTP, no
 * product-service, and no Spring context — yet it covers the merge behaviour,
 * the status-transition rules and the downstream-outage path, which is where the
 * interesting bugs live.
 */
class OrderServiceTest {

    private static final class InMemoryOrderRepository implements OrderRepository {
        private final List<Order> rows = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public Order save(Order order) {
            Order stored = order.withId(ids.getAndIncrement());
            rows.removeIf(o -> o.getId().equals(stored.getId()));
            rows.add(stored);
            return stored;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return rows.stream().filter(o -> o.getId().equals(id)).findFirst();
        }

        @Override
        public List<Order> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public List<Order> findByUsername(String username) {
            return rows.stream().filter(o -> o.getUsername().equals(username)).toList();
        }

        @Override
        public List<Order> findByStatus(OrderStatus status) {
            return rows.stream().filter(o -> o.getStatus() == status).toList();
        }

        @Override
        public Order update(Long id, Order order) {
            if (findById(id).isEmpty()) {
                throw new OrderNotFoundException(id);
            }
            return save(order.withId(id));
        }

        @Override
        public void deleteById(Long id) {
            rows.removeIf(o -> o.getId().equals(id));
        }

        @Override
        public Optional<Order> findOpenOrder(String username, Long productId) {
            return rows.stream()
                    .filter(o -> o.getUsername().equals(username)
                            && o.getProductId().equals(productId)
                            && o.getStatus() == OrderStatus.PENDING)
                    .findFirst();
        }
    }

    /** Knows about exactly two products; anything else looks like a 404. */
    private static final class FakeProductCatalog implements ProductCatalog {
        private final List<Long> lookupLog = new ArrayList<>();
        private boolean down;

        @Override
        public Optional<Product> findById(Long id) {
            lookupLog.add(id);
            if (down) {
                throw new ProductUnavailableException(id);
            }
            if (id == 1L) {
                return Optional.of(new Product(1L, "Laptop", 89999.0));
            }
            if (id == 2L) {
                return Optional.of(new Product(2L, "Phone", 49999.0));
            }
            return Optional.empty();
        }
    }

    private InMemoryOrderRepository repository;
    private FakeProductCatalog catalog;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
        catalog = new FakeProductCatalog();
        service = new OrderService(repository, catalog);
    }

    @Test
    @DisplayName("placing an order snapshots the product name")
    void placesOrder() {
        Order order = service.createOrder(new CreateOrderCommand(1L, 2, "aditi"));

        assertEquals("Laptop", order.getProductName());
        assertEquals(2, order.getQuantity());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals("aditi", order.getUsername());
    }

    @Test
    @DisplayName("a second order for the same product MERGES instead of duplicating")
    void mergesRepeatOrders() {
        // Three "add to cart" clicks should give one order for three units, not
        // three orders that get dispatched separately.
        service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        Order merged = service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));

        assertEquals(1, repository.findAll().size());
        assertEquals(3, merged.getQuantity());
    }

    @Test
    @DisplayName("orders from different customers do not merge")
    void doesNotMergeAcrossCustomers() {
        service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        service.createOrder(new CreateOrderCommand(1L, 1, "someone-else"));

        assertEquals(2, repository.findAll().size());
    }

    @Test
    @DisplayName("a non-PENDING order is not merged into")
    void doesNotMergeIntoShippedOrder() {
        Order first = service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        service.updateStatus(first.getId(), OrderStatus.PROCESSING);
        service.updateStatus(first.getId(), OrderStatus.SHIPPED);

        service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));

        assertEquals(2, repository.findAll().size());
    }

    @Test
    @DisplayName("an unknown product is rejected as a 502-style outage, not stored")
    void rejectsUnknownProduct() {
        assertThrows(ProductUnavailableException.class,
                () -> service.createOrder(new CreateOrderCommand(999L, 1, "aditi")));

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("a product-service outage is reported, never mistaken for 'no such product'")
    void reportsDownstreamOutage() {
        // If the outage returned Optional.empty() the service would happily
        // accept orders for products that do exist, and the user would see a
        // confusing "product not found" for a temporary network blip.
        catalog.down = true;

        assertThrows(ProductUnavailableException.class,
                () -> service.createOrder(new CreateOrderCommand(1L, 1, "aditi")));
    }

    @Test
    @DisplayName("a missing username is rejected by the domain")
    void rejectsMissingUsername() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderCommand(1L, 1, null)));
    }

    @Test
    @DisplayName("walks the happy path PENDING -> PROCESSING -> SHIPPED -> DELIVERED")
    void walksHappyPath() {
        Order order = service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));

        order = service.updateStatus(order.getId(), OrderStatus.PROCESSING);
        order = service.updateStatus(order.getId(), OrderStatus.SHIPPED);
        order = service.updateStatus(order.getId(), OrderStatus.DELIVERED);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    @DisplayName("refuses an illegal transition (SHIPPED -> PENDING)")
    void refusesIllegalTransition() {
        Order order = service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        order = service.updateStatus(order.getId(), OrderStatus.PROCESSING);
        order = service.updateStatus(order.getId(), OrderStatus.SHIPPED);

        assertThrows(InvalidOrderException.class,
                () -> service.updateStatus(order.getId(), OrderStatus.PENDING));
    }

    @Test
    @DisplayName("a DELIVERED order is terminal")
    void deliveredIsTerminal() {
        Order order = service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        service.updateStatus(order.getId(), OrderStatus.PROCESSING);
        service.updateStatus(order.getId(), OrderStatus.SHIPPED);
        Order delivered = service.updateStatus(order.getId(), OrderStatus.DELIVERED);

        assertThrows(InvalidOrderException.class,
                () -> service.updateStatus(delivered.getId(), OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("a PENDING order may be cancelled")
    void pendingMayBeCancelled() {
        Order order = service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));

        assertEquals(OrderStatus.CANCELLED, service.updateStatus(order.getId(), OrderStatus.CANCELLED).getStatus());
    }

    @Test
    @DisplayName("updating an unknown order throws OrderNotFoundException")
    void updateUnknownOrder() {
        assertThrows(OrderNotFoundException.class,
                () -> service.updateStatus(404L, OrderStatus.PROCESSING));
    }

    @Test
    @DisplayName("a full-field update keeps the product name snapshot")
    void fullUpdateKeepsSnapshot() {
        Order order = service.createOrder(new CreateOrderCommand(1L, 2, "aditi"));

        Order updated = service.updateOrder(
                new UpdateOrderCommand(order.getId(), null, 5, null, OrderStatus.PROCESSING));

        assertEquals(5, updated.getQuantity());
        assertEquals(OrderStatus.PROCESSING, updated.getStatus());
        assertEquals("Laptop", updated.getProductName());
    }

    @Test
    @DisplayName("deleting an unknown order throws rather than silently succeeding")
    void deleteUnknownThrows() {
        assertThrows(OrderNotFoundException.class, () -> service.deleteOrder(404L));
    }

    @Test
    @DisplayName("queries delegate to the repository")
    void queriesDelegate() {
        service.createOrder(new CreateOrderCommand(1L, 1, "aditi"));
        service.createOrder(new CreateOrderCommand(2L, 1, "aditi"));

        assertEquals(2, service.getAllOrders().size());
        assertEquals(1, service.getOrdersByUsername("aditi").size());
        assertEquals(2, service.getOrdersByStatus(OrderStatus.PENDING).size());
    }
}
