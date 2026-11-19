package dev.edward.ecommerce.checkout_service.domain.order;

import java.util.UUID;

public record OrderId(UUID value) {

	public OrderId {
		if (value == null) {
			throw new IllegalArgumentException("Order ID is required");
		}
	}

	public static OrderId generate() {
		return new OrderId(UUID.randomUUID());
	}

	public static OrderId from(String value) {
		return new OrderId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
