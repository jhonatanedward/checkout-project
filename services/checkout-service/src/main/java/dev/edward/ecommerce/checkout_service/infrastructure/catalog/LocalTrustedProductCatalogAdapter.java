package dev.edward.ecommerce.checkout_service.infrastructure.catalog;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.edward.ecommerce.checkout_service.application.order.OrderCreationException;
import dev.edward.ecommerce.checkout_service.application.port.out.TrustedProductCatalog;
import dev.edward.ecommerce.checkout_service.domain.product.TrustedProduct;
import dev.edward.ecommerce.checkout_service.infrastructure.config.TrustedProductsProperties;

@Component
public final class LocalTrustedProductCatalogAdapter implements TrustedProductCatalog {

	private final TrustedProductsProperties properties;

	public LocalTrustedProductCatalogAdapter(TrustedProductsProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<TrustedProduct> findById(String productId) {
		if (this.properties == null || this.properties.currency() == null || this.properties.currency().isBlank()
				|| this.properties.products() == null || this.properties.products().isEmpty()) {
			throw new OrderCreationException.CatalogUnavailable("Trusted product catalog is incomplete");
		}
		TrustedProductsProperties.Product product = this.properties.products().get(productId);
		if (product == null) {
			return Optional.empty();
		}
		return Optional.of(new TrustedProduct(productId, product.name(), product.price(), this.properties.currency(),
				product.active()));
	}
}
