package dev.edward.ecommerce.checkout_service.application.order;

import dev.edward.ecommerce.checkout_service.domain.order.Order;

public record CreateOrderResult(Order order, boolean replayed) {
}
