package dev.edward.ecommerce.checkout_service.application.order;

import java.util.List;

public record CreateOrderCommand(String idempotencyKey, List<Item> items) {

	public record Item(String productId, int quantity) {
	}
}
