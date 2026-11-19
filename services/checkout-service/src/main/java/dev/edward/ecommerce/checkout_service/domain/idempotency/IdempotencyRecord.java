package dev.edward.ecommerce.checkout_service.domain.idempotency;

import java.time.Instant;

import dev.edward.ecommerce.checkout_service.domain.order.OrderId;

public record IdempotencyRecord(String key, String contentHash, OrderId orderId, Instant createdAt,
		Instant expiresAt) {

	public IdempotencyRecord {
		if (key == null || key.isBlank() || contentHash == null || contentHash.isBlank() || orderId == null
				|| createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("Idempotency record fields are invalid");
		}
	}

	public boolean isActiveAt(Instant instant) {
		return expiresAt.isAfter(instant);
	}
}
