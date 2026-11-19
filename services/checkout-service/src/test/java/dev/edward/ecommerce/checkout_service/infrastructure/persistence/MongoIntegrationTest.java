package dev.edward.ecommerce.checkout_service.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import dev.edward.ecommerce.checkout_service.application.order.OrderCreationException;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderCreationTransaction;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OutboxRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.TrustedProductCatalog;
import dev.edward.ecommerce.checkout_service.domain.event.OrderCreatedEvent;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;
import dev.edward.ecommerce.checkout_service.domain.product.TrustedProduct;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.MongoOrderRepositoryAdapter;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.MongoOutboxRepositoryAdapter;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.IdempotencyDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OrderDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OutboxEventDocument;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoIntegrationTest.TestOverrides.class)
public abstract class MongoIntegrationTest {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

	@org.springframework.beans.factory.annotation.Autowired
	protected MongoTemplate mongoTemplate;

	@org.springframework.beans.factory.annotation.Autowired
	protected TestCatalog testCatalog;

	@org.springframework.beans.factory.annotation.Autowired
	protected TestClock testClock;

	@org.springframework.beans.factory.annotation.Autowired
	protected TestOrderRepository testOrderRepository;

	@org.springframework.beans.factory.annotation.Autowired
	protected TestOutboxRepository testOutboxRepository;

	@org.springframework.beans.factory.annotation.Autowired
	protected TestTransaction testTransaction;

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("checkout-service"));
	}

	@org.junit.jupiter.api.BeforeEach
	protected void resetIntegrationState() {
		this.mongoTemplate.remove(new Query(), OrderDocument.class);
		this.mongoTemplate.remove(new Query(), OutboxEventDocument.class);
		this.mongoTemplate.remove(new Query(), IdempotencyDocument.class);
		this.testCatalog.reset();
		this.testClock.reset();
		this.testOrderRepository.clearFailure();
		this.testOutboxRepository.clearFailure();
		this.testTransaction.clearFailure();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestOverrides {

		@Bean
		@Primary
		TestCatalog testCatalog() {
			return new TestCatalog();
		}

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}

		@Bean
		@Primary
		TestOrderRepository testOrderRepository(MongoOrderRepositoryAdapter delegate) {
			return new TestOrderRepository(delegate);
		}

		@Bean
		@Primary
		TestOutboxRepository testOutboxRepository(MongoOutboxRepositoryAdapter delegate) {
			return new TestOutboxRepository(delegate);
		}

		@Bean
		@Primary
		TestTransaction testTransaction(MongoOrderCreationTransaction delegate) {
			return new TestTransaction(delegate);
		}
	}

	protected static final class TestCatalog implements TrustedProductCatalog {

		private final Map<String, TrustedProduct> products = new ConcurrentHashMap<>();

		private volatile boolean available = true;

		void reset() {
			this.products.clear();
			this.products.put("WIDGET_A",
					new TrustedProduct("WIDGET_A", "Widget A", new BigDecimal("29.99"), "USD", true));
			this.products.put("WIDGET_B",
					new TrustedProduct("WIDGET_B", "Widget B", new BigDecimal("40.01"), "USD", true));
			this.available = true;
		}

		public void put(TrustedProduct product) {
			this.products.put(product.id(), product);
		}

		public void setAvailable(boolean available) {
			this.available = available;
		}

		@Override
		public Optional<TrustedProduct> findById(String productId) {
			if (!this.available) {
				throw new OrderCreationException.CatalogUnavailable("Test catalog is unavailable");
			}
			return Optional.ofNullable(this.products.get(productId));
		}
	}

	protected static final class TestOrderRepository implements OrderRepository {

		private final MongoOrderRepositoryAdapter delegate;

		private volatile boolean failAfterSave;

		TestOrderRepository(MongoOrderRepositoryAdapter delegate) {
			this.delegate = delegate;
		}

		public void failNextSave() {
			this.failAfterSave = true;
		}

		public void clearFailure() {
			this.failAfterSave = false;
		}

		@Override
		public Order save(Order order) {
			Order saved = this.delegate.save(order);
			if (this.failAfterSave) {
				this.failAfterSave = false;
				throw new IllegalStateException("Injected order persistence failure");
			}
			return saved;
		}

		@Override
		public Optional<Order> findById(OrderId orderId) {
			return this.delegate.findById(orderId);
		}
	}

	protected static final class TestOutboxRepository implements OutboxRepository {

		private final MongoOutboxRepositoryAdapter delegate;

		private volatile boolean failAfterSave;

		TestOutboxRepository(MongoOutboxRepositoryAdapter delegate) {
			this.delegate = delegate;
		}

		public void failNextSave() {
			this.failAfterSave = true;
		}

		public void clearFailure() {
			this.failAfterSave = false;
		}

		@Override
		public void save(OrderCreatedEvent event) {
			this.delegate.save(event);
			if (this.failAfterSave) {
				this.failAfterSave = false;
				throw new IllegalStateException("Injected outbox persistence failure");
			}
		}
	}

	protected static final class TestTransaction implements OrderCreationTransaction {

		private final MongoOrderCreationTransaction delegate;

		private volatile boolean unknownBeforeCommit;

		private volatile boolean unknownAfterCommit;

		TestTransaction(MongoOrderCreationTransaction delegate) {
			this.delegate = delegate;
		}

		public void simulateUnknownBeforeCommit() {
			this.unknownBeforeCommit = true;
		}

		public void simulateUnknownAfterCommit() {
			this.unknownAfterCommit = true;
		}

		public void clearFailure() {
			this.unknownBeforeCommit = false;
			this.unknownAfterCommit = false;
		}

		@Override
		public <T> T execute(java.util.function.Supplier<T> operation) {
			if (this.unknownBeforeCommit) {
				this.unknownBeforeCommit = false;
				throw new OrderCreationException.Retryable("Injected unknown transaction outcome");
			}
			T result = this.delegate.execute(operation);
			if (this.unknownAfterCommit) {
				this.unknownAfterCommit = false;
				throw new OrderCreationException.Retryable("Injected unknown transaction outcome");
			}
			return result;
		}
	}

	protected static final class TestClock extends Clock {

		private volatile Instant instant;

		TestClock() {
			reset();
		}

		void reset() {
			this.instant = Instant.now().minus(Duration.ofHours(1));
		}

		public void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException("Test clock is fixed to UTC");
		}

		@Override
		public Instant instant() {
			return this.instant;
		}
	}
}
