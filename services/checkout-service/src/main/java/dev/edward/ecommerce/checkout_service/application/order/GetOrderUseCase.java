package dev.edward.ecommerce.checkout_service.application.order;

import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;

public final class GetOrderUseCase {

	private final OrderRepository orderRepository;

	public GetOrderUseCase(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	public Order get(String orderId) {
		final OrderId id;
		try {
			id = OrderId.from(orderId);
		}
		catch (IllegalArgumentException exception) {
			throw new OrderCreationException.NotFound(orderId);
		}
		return this.orderRepository.findById(id).orElseThrow(() -> new OrderCreationException.NotFound(orderId));
	}
}
