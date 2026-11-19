package dev.edward.ecommerce.checkout_service.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

class CreateOrderIdempotencyTest {

	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void replaysBeforeCatalogLookupAndRejectsChangedContent() {
		TestFixture fixture = new TestFixture(Clock.fixed(START, ZoneOffset.UTC));
		fixture.catalog.available = true;
		CreateOrderCommand original = command("key", 2);
		Order first = fixture.useCase.create(original).order();
		fixture.catalog.available = false;

		CreateOrderResult replay = fixture.useCase.create(original);

		assertEquals(first.id(), replay.order().id());
		assertEquals(true, replay.replayed());
		assertEquals(1, fixture.orders.size());
		assertThrows(OrderCreationException.IdempotencyConflict.class,
				() -> fixture.useCase.create(command("key", 3)));
	}

	@Test
	void reusesExpiredKeyEvenBeforeTtlCleanup() {
		MutableClock clock = new MutableClock(START);
		TestFixture fixture = new TestFixture(clock);
		fixture.catalog.available = true;
		Order first = fixture.useCase.create(command("key", 1)).order();
		clock.set(START.plus(Duration.ofHours(24)));

		Order second = fixture.useCase.create(command("key", 1)).order();

		assertNotEquals(first.id(), second.id());
		assertEquals(2, fixture.orders.size());
		assertEquals(2, fixture.events.size());
	}

	@Test
	void boundsIdempotencyClaimRetries() {
		AtomicInteger attempts = new AtomicInteger();
		OrderCreationTransaction transaction = new OrderCreationTransaction() {
			@Override
			public <T> T execute(java.util.function.Supplier<T> operation) {
				attempts.incrementAndGet();
				return operation.get();
			}
		};
		TestFixture fixture = new TestFixture(Clock.fixed(START, ZoneOffset.UTC), transaction);
		fixture.claimAlwaysConflicts = true;
		fixture.catalog.available = true;

		assertThrows(OrderCreationException.Retryable.class, () -> fixture.useCase.create(command("key", 1)));
		assertEquals(3, attempts.get());
	}

	@Test
	void retryAfterUnknownCommitReturnsTheCommittedOrder() {
		AtomicInteger attempts = new AtomicInteger();
		OrderCreationTransaction transaction = new OrderCreationTransaction() {
			@Override
			public <T> T execute(java.util.function.Supplier<T> operation) {
				if (attempts.getAndIncrement() == 0) {
					operation.get();
					throw new OrderCreationException.Retryable("Commit outcome is unknown");
				}
				return operation.get();
			}
		};
		TestFixture fixture = new TestFixture(Clock.fixed(START, ZoneOffset.UTC), transaction);
		fixture.catalog.available = true;

		assertThrows(OrderCreationException.Retryable.class, () -> fixture.useCase.create(command("key", 1)));
		Order replay = fixture.useCase.create(command("key", 1)).order();

		assertEquals(1, fixture.orders.size());
		assertEquals(1, fixture.events.size());
		assertEquals(fixture.orders.keySet().iterator().next(), replay.id());
	}

	private static CreateOrderCommand command(String key, int quantity) {
		return new CreateOrderCommand(key, List.of(new CreateOrderCommand.Item("A", quantity)));
	}

	private static final class TestFixture {

		private final Map<OrderId, Order> orders = new HashMap<>();

		private final Map<String, IdempotencyRecord> records = new HashMap<>();

		private final java.util.List<OrderCreatedEvent> events = new java.util.ArrayList<>();

		private final TestCatalog catalog = new TestCatalog();

		private final CreateOrderUseCase useCase;

		private boolean claimAlwaysConflicts;

		TestFixture(Clock clock) {
			this(clock, SupplierTransaction.INSTANCE);
		}

		TestFixture(Clock clock, OrderCreationTransaction transaction) {
			OrderRepository orderRepository = new OrderRepository() {
				@Override
				public Order save(Order order) {
					orders.put(order.id(), order);
					return order;
				}

				@Override
				public Optional<Order> findById(OrderId id) {
					return Optional.ofNullable(orders.get(id));
				}
			};
			OutboxRepository outboxRepository = events::add;
			IdempotencyRepository idempotencyRepository = new IdempotencyRepository() {
				@Override
				public Optional<IdempotencyRecord> findByKey(String key) {
					return Optional.ofNullable(records.get(key));
				}

				@Override
				public void claim(IdempotencyRecord record, Instant now) {
					if (claimAlwaysConflicts || (records.containsKey(record.key())
							&& records.get(record.key()).isActiveAt(now))) {
						throw new OrderCreationException.IdempotencyWriteConflict(null);
					}
					records.put(record.key(), record);
				}
			};
			this.useCase = new CreateOrderUseCase(this.catalog, orderRepository, outboxRepository,
					idempotencyRepository, transaction, clock);
		}
	}

	private static final class TestCatalog implements TrustedProductCatalog {

		private boolean available;

		@Override
		public Optional<TrustedProduct> findById(String productId) {
			if (!this.available) {
				throw new OrderCreationException.CatalogUnavailable("Catalog is unavailable");
			}
			return Optional.of(new TrustedProduct("A", "A product", new BigDecimal("2.50"), "USD", true));
		}
	}

	private enum SupplierTransaction implements OrderCreationTransaction {
		INSTANCE;

		@Override
		public <T> T execute(java.util.function.Supplier<T> operation) {
			return operation.get();
		}
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}
	}
}
