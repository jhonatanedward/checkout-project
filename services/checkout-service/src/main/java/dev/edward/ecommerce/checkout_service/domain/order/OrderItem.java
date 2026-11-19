package dev.edward.ecommerce.checkout_service.domain.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrderItem(String productId, String productName, int quantity, BigDecimal unitPrice, String currency,
		BigDecimal subtotal) {

	public OrderItem(String productId, String productName, int quantity, BigDecimal unitPrice, String currency) {
		this(requireText(productId, "Product ID"), requireText(productName, "Product name"), quantity,
				normalizePrice(unitPrice), requireText(currency, "Currency"),
				normalizePrice(unitPrice).multiply(BigDecimal.valueOf(quantity)));
	}

	public OrderItem {
		requireText(productId, "Product ID");
		requireText(productName, "Product name");
		requireText(currency, "Currency");
		if (quantity <= 0) {
			throw new IllegalArgumentException("Quantity must be positive");
		}
		unitPrice = normalizePrice(unitPrice);
		subtotal = normalizePrice(subtotal);
		if (subtotal.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
			throw new IllegalArgumentException("Subtotal must equal quantity multiplied by unit price");
		}
	}

	private static BigDecimal normalizePrice(BigDecimal price) {
		if (price == null || price.signum() <= 0) {
			throw new IllegalArgumentException("Price must be positive");
		}
		try {
			return price.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException exception) {
			throw new IllegalArgumentException("Price must be exactly representable to two decimal places", exception);
		}
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
