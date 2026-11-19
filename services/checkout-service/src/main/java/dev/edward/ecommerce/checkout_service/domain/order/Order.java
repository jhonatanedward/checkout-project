package dev.edward.ecommerce.checkout_service.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Order {

	private final OrderId id;

	private final OrderStatus status;

	private final BigDecimal totalAmount;

	private final String currency;

	private final List<OrderItem> items;

	private final String idempotencyKey;

	private final Instant createdAt;

	public Order(OrderId id, OrderStatus status, String currency, List<OrderItem> items, String idempotencyKey,
			Instant createdAt) {
		if (id == null || status == null || currency == null || currency.isBlank() || items == null || items.isEmpty()
				|| idempotencyKey == null || idempotencyKey.isBlank() || createdAt == null) {
			throw new IllegalArgumentException("Order fields are required and an order must contain at least one item");
		}
		this.id = id;
		this.status = status;
		this.currency = currency;
		this.items = List.copyOf(items);
		this.idempotencyKey = idempotencyKey;
		this.createdAt = createdAt;
		Set<String> productIds = new HashSet<>();
		BigDecimal total = BigDecimal.ZERO.setScale(2);
		for (OrderItem item : this.items) {
			if (!productIds.add(item.productId())) {
				throw new IllegalArgumentException("An order cannot contain the same product more than once");
			}
			if (!currency.equals(item.currency())) {
				throw new IllegalArgumentException("All order items must use the order currency");
			}
			total = total.add(item.subtotal());
		}
		if (total.signum() <= 0) {
			throw new IllegalArgumentException("Order total must be positive");
		}
		this.totalAmount = total;
	}

	public static Order create(String idempotencyKey, String currency, List<OrderItem> items, Instant createdAt) {
		return new Order(OrderId.generate(), OrderStatus.CREATED, currency, items, idempotencyKey, createdAt);
	}

	public OrderId id() {
		return id;
	}

	public OrderStatus status() {
		return status;
	}

	public BigDecimal totalAmount() {
		return totalAmount;
	}

	public String currency() {
		return currency;
	}

	public List<OrderItem> items() {
		return items;
	}

	public String idempotencyKey() {
		return idempotencyKey;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
