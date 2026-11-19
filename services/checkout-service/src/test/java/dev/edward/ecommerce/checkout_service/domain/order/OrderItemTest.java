package dev.edward.ecommerce.checkout_service.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class OrderItemTest {

	@Test
	void rejectsNonPositiveQuantityAndPrice() {
		assertThrows(IllegalArgumentException.class, () -> item(0, "29.99"));
		assertThrows(IllegalArgumentException.class, () -> item(-1, "29.99"));
		assertThrows(IllegalArgumentException.class, () -> item(1, "0.00"));
		assertThrows(IllegalArgumentException.class, () -> item(1, "-1.00"));
	}

	@Test
	void rejectsPriceThatWouldRequireRounding() {
		assertThrows(IllegalArgumentException.class, () -> item(1, "1.001"));
	}

	@Test
	void calculatesExactSubtotal() {
		OrderItem item = item(3, "29.99");

		assertEquals(new BigDecimal("29.99"), item.unitPrice());
		assertEquals(new BigDecimal("89.97"), item.subtotal());
	}

	private static OrderItem item(int quantity, String unitPrice) {
		return new OrderItem("WIDGET_A", "Widget A", quantity, new BigDecimal(unitPrice), "USD");
	}
}
