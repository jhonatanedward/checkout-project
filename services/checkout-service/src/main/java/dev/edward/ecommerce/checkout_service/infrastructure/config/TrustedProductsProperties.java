package dev.edward.ecommerce.checkout_service.infrastructure.config;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "checkout.catalog")
public record TrustedProductsProperties(String currency, Map<String, Product> products) {

	public record Product(String name, BigDecimal price, boolean active) {
	}
}
