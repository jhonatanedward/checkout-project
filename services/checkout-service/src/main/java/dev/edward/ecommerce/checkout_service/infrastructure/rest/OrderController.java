package dev.edward.ecommerce.checkout_service.infrastructure.rest;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.edward.ecommerce.checkout_service.application.order.CreateOrderCommand;
import dev.edward.ecommerce.checkout_service.application.order.CreateOrderResult;
import dev.edward.ecommerce.checkout_service.application.order.CreateOrderUseCase;
import dev.edward.ecommerce.checkout_service.application.order.GetOrderUseCase;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.infrastructure.rest.dto.CreateOrderRequest;
import dev.edward.ecommerce.checkout_service.infrastructure.rest.dto.OrderResponse;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final CreateOrderUseCase createOrderUseCase;

	private final GetOrderUseCase getOrderUseCase;

	public OrderController(CreateOrderUseCase createOrderUseCase, GetOrderUseCase getOrderUseCase) {
		this.createOrderUseCase = createOrderUseCase;
		this.getOrderUseCase = getOrderUseCase;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		List<CreateOrderCommand.Item> items = request.items().stream()
				.map(item -> new CreateOrderCommand.Item(item.productId(), item.quantity())).toList();
		CreateOrderResult result = this.createOrderUseCase.create(new CreateOrderCommand(idempotencyKey, items));
		return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
				.body(OrderResponse.from(result.order()));
	}

	@GetMapping("/{orderId}")
	public OrderResponse get(@PathVariable String orderId) {
		Order order = this.getOrderUseCase.get(orderId);
		return OrderResponse.from(order);
	}
}
