package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document;

import java.math.BigDecimal;

import org.bson.types.Decimal128;

import dev.edward.ecommerce.checkout_service.domain.order.OrderItem;

public record OrderItemDocument(String productId, String productName, int quantity, Decimal128 unitPrice,
		String currency, Decimal128 subtotal) {

	public static OrderItemDocument from(OrderItem item) {
		return new OrderItemDocument(item.productId(), item.productName(), item.quantity(), decimal128(item.unitPrice()),
				item.currency(), decimal128(item.subtotal()));
	}

	public OrderItem toDomain() {
		return new OrderItem(this.productId, this.productName, this.quantity, this.unitPrice.bigDecimalValue(),
				this.currency, this.subtotal.bigDecimalValue());
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
