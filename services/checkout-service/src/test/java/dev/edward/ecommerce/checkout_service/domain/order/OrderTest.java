package dev.edward.ecommerce.checkout_service.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class OrderTest {

	@Test
	void rejectsEmptyOrders() {
		assertThrows(IllegalArgumentException.class,
				() -> new Order(OrderId.generate(), OrderStatus.CREATED, "USD", List.of(), "key", Instant.now()));
	}

	@Test
	void rejectsDuplicateProductsAndIncompatibleCurrency() {
		OrderItem item = item("WIDGET_A", "USD", "29.99");

		assertThrows(IllegalArgumentException.class,
				() -> order(List.of(item, item("WIDGET_A", "USD", "10.00"))));
		assertThrows(IllegalArgumentException.class,
				() -> order(List.of(item, item("WIDGET_B", "EUR", "10.00"))));
	}

	@Test
	void calculatesTotalAndKeepsItemsImmutable() {
		List<OrderItem> source = new java.util.ArrayList<>(List.of(item("WIDGET_A", "USD", "29.99"),
				item("WIDGET_B", "USD", "40.01")));
		Order order = order(source);
		source.clear();

		assertEquals(new BigDecimal("70.00"), order.totalAmount());
		assertEquals(2, order.items().size());
		assertThrows(UnsupportedOperationException.class, () -> order.items().clear());
	}

	private static Order order(List<OrderItem> items) {
		return new Order(OrderId.generate(), OrderStatus.CREATED, "USD", items, "key", Instant.now());
	}

	private static OrderItem item(String id, String currency, String price) {
		return new OrderItem(id, id, 1, new BigDecimal(price), currency);
	}
}
