package dev.edward.ecommerce.checkout_service.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dev.edward.ecommerce.checkout_service.domain.order.Order;

public record OrderResponse(String id, String status, BigDecimal totalAmount, String currency,
		List<OrderItemResponse> items, String idempotencyKey, Instant createdAt) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(order.id().toString(), order.status().name(), order.totalAmount(), order.currency(),
				order.items().stream().map(OrderItemResponse::from).toList(), order.idempotencyKey(), order.createdAt());
	}
}
