package dev.edward.ecommerce.checkout_service.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalMongoOrderCreation {

	@Transactional
	public <T> T execute(Supplier<T> operation) {
		return operation.get();
	}
}
