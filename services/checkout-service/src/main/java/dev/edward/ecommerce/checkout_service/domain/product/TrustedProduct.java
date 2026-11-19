package dev.edward.ecommerce.checkout_service.domain.product;

import java.math.BigDecimal;

public record TrustedProduct(String id, String name, BigDecimal unitPrice, String currency, boolean active) {
}
