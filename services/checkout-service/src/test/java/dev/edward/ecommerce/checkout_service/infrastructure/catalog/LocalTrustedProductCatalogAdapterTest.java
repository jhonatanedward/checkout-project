package dev.edward.ecommerce.checkout_service.infrastructure.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.edward.ecommerce.checkout_service.application.order.OrderCreationException;
import dev.edward.ecommerce.checkout_service.infrastructure.config.TrustedProductsProperties;

class LocalTrustedProductCatalogAdapterTest {

	@Test
	void returnsConfiguredAuthoritativeValuesAndOmitsUnknownProducts() {
		LocalTrustedProductCatalogAdapter catalog = catalog("USD",
				Map.of("A", new TrustedProductsProperties.Product("Widget A", new BigDecimal("29.99"), true)));

		assertEquals("Widget A", catalog.findById("A").orElseThrow().name());
		assertEquals(new BigDecimal("29.99"), catalog.findById("A").orElseThrow().unitPrice());
		assertFalse(catalog.findById("UNKNOWN").isPresent());
	}

	@Test
	void distinguishesIncompleteSourceFromInvalidSpecificProductData() {
		LocalTrustedProductCatalogAdapter incomplete = catalog(" ", Map.of());
		assertThrows(OrderCreationException.CatalogUnavailable.class, () -> incomplete.findById("A"));

		LocalTrustedProductCatalogAdapter invalidProduct = catalog("USD",
				Map.of("A", new TrustedProductsProperties.Product("", new BigDecimal("1.001"), true)));
		assertEquals("", invalidProduct.findById("A").orElseThrow().name());
	}

	private static LocalTrustedProductCatalogAdapter catalog(String currency,
			Map<String, TrustedProductsProperties.Product> products) {
		return new LocalTrustedProductCatalogAdapter(new TrustedProductsProperties(currency, products));
	}
}
