package dev.edward.ecommerce.checkout_service.application.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.edward.ecommerce.checkout_service.application.order.CreateOrderCommand;

class OrderRequestFingerprintTest {

	@Test
	void normalizesItemOrderButIncludesQuantities() {
		String first = OrderRequestFingerprint.create(List.of(item("A", 2), item("B", 1)));
		String reordered = OrderRequestFingerprint.create(List.of(item("B", 1), item("A", 2)));
		String different = OrderRequestFingerprint.create(List.of(item("A", 3), item("B", 1)));

		assertEquals(first, reordered);
		assertNotEquals(first, different);
	}

	private static CreateOrderCommand.Item item(String id, int quantity) {
		return new CreateOrderCommand.Item(id, quantity);
	}
}
