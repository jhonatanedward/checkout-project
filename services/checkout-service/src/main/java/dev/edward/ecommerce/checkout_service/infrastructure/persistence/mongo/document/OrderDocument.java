package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.bson.types.Decimal128;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;
import dev.edward.ecommerce.checkout_service.domain.order.OrderStatus;

@Document(collection = "orders")
public record OrderDocument(@Id String id, OrderStatus status, Decimal128 totalAmount, String currency,
		List<OrderItemDocument> items, String idempotencyKey, Instant createdAt) {

	public static OrderDocument from(Order order) {
		return new OrderDocument(order.id().toString(), order.status(), decimal128(order.totalAmount()), order.currency(),
				order.items().stream().map(OrderItemDocument::from).toList(), order.idempotencyKey(), order.createdAt());
	}

	public Order toDomain() {
		return new Order(new OrderId(UUID.fromString(this.id)), this.status, this.currency,
				this.items.stream().map(OrderItemDocument::toDomain).toList(), this.idempotencyKey, this.createdAt);
	}

	private static Decimal128 decimal128(BigDecimal value) {
		try {
			return new Decimal128(value);
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Monetary value cannot be represented as Decimal128", exception);
		}
	}
}
