package dev.edward.ecommerce.checkout_service.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.edward.ecommerce.checkout_service.domain.order.OrderId;

public record OrderCreatedEvent(UUID id, OrderId orderId, String eventType, Instant createdAt, Payload payload) {

	public static final String TYPE = "ORDER_CREATED";

	public OrderCreatedEvent(OrderId orderId, Instant createdAt, Payload payload) {
		this(UUID.randomUUID(), orderId, TYPE, createdAt, payload);
	}

	public record Payload(BigDecimal totalAmount, String currency, int itemCount, String idempotencyKey) {
	}
}
