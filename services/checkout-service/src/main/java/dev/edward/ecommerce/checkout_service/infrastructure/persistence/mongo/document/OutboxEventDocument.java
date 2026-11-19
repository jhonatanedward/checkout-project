package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document;

import java.time.Instant;
import java.util.UUID;

import org.bson.types.Decimal128;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import dev.edward.ecommerce.checkout_service.domain.event.OrderCreatedEvent;

@Document(collection = "order_events")
public record OutboxEventDocument(@Id String id, @Indexed(unique = true) String orderId, String eventType,
		Instant createdAt, Payload payload) {

	public static OutboxEventDocument from(OrderCreatedEvent event) {
		return new OutboxEventDocument(event.id().toString(), event.orderId().toString(), event.eventType(),
				event.createdAt(), new Payload(new Decimal128(event.payload().totalAmount()), event.payload().currency(),
						event.payload().itemCount(), event.payload().idempotencyKey()));
	}

	public record Payload(Decimal128 totalAmount, String currency, int itemCount, String idempotencyKey) {
	}

	public OrderCreatedEvent toDomain() {
		return new OrderCreatedEvent(UUID.fromString(this.id),
				dev.edward.ecommerce.checkout_service.domain.order.OrderId.from(this.orderId), this.eventType,
				this.createdAt, new OrderCreatedEvent.Payload(this.payload.totalAmount().bigDecimalValue(),
						this.payload.currency(), this.payload.itemCount(), this.payload.idempotencyKey()));
	}
}
