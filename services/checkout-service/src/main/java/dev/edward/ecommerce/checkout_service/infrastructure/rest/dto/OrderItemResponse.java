package dev.edward.ecommerce.checkout_service.infrastructure.rest.dto;

import java.math.BigDecimal;

import dev.edward.ecommerce.checkout_service.domain.order.OrderItem;

public record OrderItemResponse(String productId, String productName, int quantity, BigDecimal unitPrice,
		BigDecimal subtotal) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(item.productId(), item.productName(), item.quantity(), item.unitPrice(),
				item.subtotal());
	}
}
