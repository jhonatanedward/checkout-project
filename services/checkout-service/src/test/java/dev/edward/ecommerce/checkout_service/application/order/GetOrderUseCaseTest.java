package dev.edward.ecommerce.checkout_service.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;
import dev.edward.ecommerce.checkout_service.domain.order.OrderItem;
import dev.edward.ecommerce.checkout_service.domain.order.OrderStatus;

class GetOrderUseCaseTest {

	@Test
	void returnsSavedOrderAndReportsUnknownIds() {
		Order saved = new Order(OrderId.generate(), OrderStatus.CREATED, "USD",
				List.of(new OrderItem("A", "A product", 1, new BigDecimal("2.50"), "USD")), "key",
				Instant.parse("2026-01-01T00:00:00Z"));
		OrderRepository repository = new OrderRepository() {
			@Override
			public Order save(Order order) {
				return order;
			}

			@Override
			public Optional<Order> findById(OrderId orderId) {
				return saved.id().equals(orderId) ? Optional.of(saved) : Optional.empty();
			}
		};
		GetOrderUseCase useCase = new GetOrderUseCase(repository);
		String missingId = OrderId.generate().toString();

		assertEquals(saved, useCase.get(saved.id().toString()));
		assertThrows(OrderCreationException.NotFound.class, () -> useCase.get(missingId));
		assertThrows(OrderCreationException.NotFound.class, () -> useCase.get("not-a-uuid"));
	}
}
