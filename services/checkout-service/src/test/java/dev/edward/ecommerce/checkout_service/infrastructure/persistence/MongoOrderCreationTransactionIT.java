package dev.edward.ecommerce.checkout_service.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.edward.ecommerce.checkout_service.domain.event.OrderCreatedEvent;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderItem;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OrderDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OutboxEventDocument;

@Testcontainers(disabledWithoutDocker = true)
class MongoOrderCreationTransactionIT extends MongoIntegrationTest {

	@Test
	void commitsOrderAndOutboxTogether() {
		Order order = order();

		this.testTransaction.execute(() -> {
			this.testOrderRepository.save(order);
			this.testOutboxRepository.save(event(order));
			return null;
		});

		assertEquals(1, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OrderDocument.class));
		assertEquals(1, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OutboxEventDocument.class));
	}

	@Test
	void rollsBackOrderWhenOrderPersistenceFails() {
		this.testOrderRepository.failNextSave();

		assertThrows(IllegalStateException.class, () -> this.testTransaction.execute(() -> {
			this.testOrderRepository.save(order());
			return null;
		}));

		assertEquals(0, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OrderDocument.class));
		assertEquals(0, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OutboxEventDocument.class));
	}

	@Test
	void rollsBackOrderAndOutboxWhenOutboxPersistenceFails() {
		Order order = order();
		this.testOutboxRepository.failNextSave();

		assertThrows(IllegalStateException.class, () -> this.testTransaction.execute(() -> {
			this.testOrderRepository.save(order);
			this.testOutboxRepository.save(event(order));
			return null;
		}));

		assertEquals(0, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OrderDocument.class));
		assertEquals(0, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OutboxEventDocument.class));
	}

	private Order order() {
		return Order.create("integration-key", "USD",
				List.of(new OrderItem("WIDGET_A", "Widget A", 1, new java.math.BigDecimal("29.99"), "USD")),
				this.testClock.instant());
	}

	private static OrderCreatedEvent event(Order order) {
		return new OrderCreatedEvent(order.id(), order.createdAt(),
				new OrderCreatedEvent.Payload(order.totalAmount(), order.currency(), order.items().size(),
						order.idempotencyKey()));
	}
}
