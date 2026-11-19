package dev.edward.ecommerce.checkout_service.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.edward.ecommerce.checkout_service.application.order.CreateOrderUseCase;
import dev.edward.ecommerce.checkout_service.application.order.GetOrderUseCase;
import dev.edward.ecommerce.checkout_service.application.port.out.IdempotencyRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderCreationTransaction;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OutboxRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.TrustedProductCatalog;

@Configuration
@EnableConfigurationProperties(TrustedProductsProperties.class)
public class CheckoutConfiguration {

	@Bean
	Clock checkoutClock() {
		return Clock.systemUTC();
	}

	@Bean
	CreateOrderUseCase createOrderUseCase(TrustedProductCatalog catalog, OrderRepository orderRepository,
			OutboxRepository outboxRepository, IdempotencyRepository idempotencyRepository,
			OrderCreationTransaction transaction, Clock checkoutClock) {
		return new CreateOrderUseCase(catalog, orderRepository, outboxRepository, idempotencyRepository, transaction,
				checkoutClock);
	}

	@Bean
	GetOrderUseCase getOrderUseCase(OrderRepository orderRepository) {
		return new GetOrderUseCase(orderRepository);
	}
}
