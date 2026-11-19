package dev.edward.ecommerce.checkout_service.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.edward.ecommerce.checkout_service.application.port.out.IdempotencyRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderCreationTransaction;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OutboxRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.TrustedProductCatalog;
import dev.edward.ecommerce.checkout_service.domain.event.OrderCreatedEvent;
import dev.edward.ecommerce.checkout_service.domain.idempotency.IdempotencyRecord;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;
import dev.edward.ecommerce.checkout_service.domain.product.TrustedProduct;

class CreateOrderUseCaseTest {

	private final MemoryState state = new MemoryState();

	private final TestCatalog catalog = new TestCatalog();

	private final CreateOrderUseCase useCase = new CreateOrderUseCase(this.catalog, this.state, this.state, this.state,
			SupplierTransaction.INSTANCE, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

	@Test
	void createsSnapshotFromTrustedCatalogAndCalculatesExactTotal() {
		this.catalog.add("WIDGET_A", "Authoritative widget", "29.99");

		Order order = this.useCase.create(command("key", item("WIDGET_A", 2))).order();

		assertEquals("Authoritative widget", order.items().get(0).productName());
		assertEquals(new BigDecimal("29.99"), order.items().get(0).unitPrice());
		assertEquals(new BigDecimal("59.98"), order.totalAmount());
		assertEquals(1, this.state.orders.size());
		assertEquals(1, this.state.events.size());
	}

	@Test
	void rejectsMissingProductsAndRepeatedLinesWithoutPersistingPartialOrders() {
		this.catalog.add("WIDGET_A", "Widget A", "29.99");

		assertThrows(OrderCreationException.ProductNotFound.class,
				() -> this.useCase.create(command("missing", item("WIDGET_A", 1), item("UNKNOWN", 1))));
		assertThrows(OrderCreationException.Validation.class,
				() -> this.useCase.create(command("duplicate", item("WIDGET_A", 1), item("WIDGET_A", 2))));

		assertEquals(0, this.state.orders.size());
		assertEquals(0, this.state.events.size());
		assertEquals(0, this.state.idempotency.size());
	}

	@Test
	void rejectsInvalidProductPriceWithoutRounding() {
		this.catalog.products.put("BAD_PRICE",
				new TrustedProduct("BAD_PRICE", "Invalid", new BigDecimal("1.001"), "USD", true));

		assertThrows(OrderCreationException.InvalidProductPrice.class,
				() -> this.useCase.create(command("bad-price", item("BAD_PRICE", 1))));
		assertEquals(0, this.state.orders.size());
	}

	@Test
	void rejectsInactiveProductsAndRecoversAfterCatalogOutage() {
		this.catalog.add("INACTIVE", "Inactive widget", "4.00", false);
		assertThrows(OrderCreationException.ProductInactive.class,
				() -> this.useCase.create(command("inactive", item("INACTIVE", 1))));

		this.catalog.available = false;
		assertThrows(OrderCreationException.CatalogUnavailable.class,
				() -> this.useCase.create(command("recovered", item("WIDGET_A", 1))));
		assertEquals(0, this.state.orders.size());
		assertEquals(0, this.state.events.size());

		this.catalog.available = true;
		this.catalog.add("WIDGET_A", "Widget A", "29.99");
		this.useCase.create(command("recovered", item("WIDGET_A", 1)));
		assertEquals(1, this.state.orders.size());
		assertEquals(1, this.state.events.size());
	}

	private static CreateOrderCommand command(String key, CreateOrderCommand.Item... items) {
		return new CreateOrderCommand(key, List.of(items));
	}

	private static CreateOrderCommand.Item item(String productId, int quantity) {
		return new CreateOrderCommand.Item(productId, quantity);
	}

	private static final class TestCatalog implements TrustedProductCatalog {

		private final Map<String, TrustedProduct> products = new HashMap<>();

		private boolean available = true;

		void add(String id, String name, String price) {
			add(id, name, price, true);
		}

		void add(String id, String name, String price, boolean active) {
			this.products.put(id, new TrustedProduct(id, name, new BigDecimal(price), "USD", active));
		}

		@Override
		public Optional<TrustedProduct> findById(String productId) {
			if (!this.available) {
				throw new OrderCreationException.CatalogUnavailable("Catalog is unavailable");
			}
			return Optional.ofNullable(this.products.get(productId));
		}
	}

	private static final class MemoryState implements OrderRepository, OutboxRepository, IdempotencyRepository {

		private final Map<OrderId, Order> orders = new HashMap<>();

		private final List<OrderCreatedEvent> events = new ArrayList<>();

		private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();

		@Override
		public Order save(Order order) {
			this.orders.put(order.id(), order);
			return order;
		}

		@Override
		public Optional<Order> findById(OrderId orderId) {
			return Optional.ofNullable(this.orders.get(orderId));
		}

		@Override
		public void save(OrderCreatedEvent event) {
			this.events.add(event);
		}

		@Override
		public Optional<IdempotencyRecord> findByKey(String key) {
			return Optional.ofNullable(this.idempotency.get(key));
		}

		@Override
		public void claim(IdempotencyRecord record, Instant now) {
			IdempotencyRecord existing = this.idempotency.get(record.key());
			if (existing != null && existing.isActiveAt(now)) {
				throw new OrderCreationException.IdempotencyWriteConflict(null);
			}
			this.idempotency.put(record.key(), record);
		}
	}

	private enum SupplierTransaction implements OrderCreationTransaction {
		INSTANCE;

		@Override
		public <T> T execute(java.util.function.Supplier<T> operation) {
			return operation.get();
		}
	}
}
