package dev.edward.ecommerce.checkout_service.infrastructure.persistence;

import java.util.function.Supplier;

import com.mongodb.MongoException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import dev.edward.ecommerce.checkout_service.application.order.OrderCreationException;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderCreationTransaction;

@Component
public class MongoOrderCreationTransaction implements OrderCreationTransaction {

	private final TransactionalMongoOrderCreation transactionalCreation;

	public MongoOrderCreationTransaction(TransactionalMongoOrderCreation transactionalCreation) {
		this.transactionalCreation = transactionalCreation;
	}

	@Override
	public <T> T execute(Supplier<T> operation) {
		try {
			return this.transactionalCreation.execute(operation);
		}
		catch (OrderCreationException.IdempotencyWriteConflict | OrderCreationException.Retryable exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			MongoException mongoException = findCause(exception, MongoException.class);
			if (mongoException != null && mongoException.hasErrorLabel("UnknownTransactionCommitResult")) {
				throw new OrderCreationException.Retryable(
						"MongoDB could not confirm the transaction commit; retry with the same idempotency key");
			}
			if (exception instanceof DuplicateKeyException || exception instanceof DataIntegrityViolationException
					|| exception instanceof TransientDataAccessException
					|| (mongoException != null && (mongoException.getCode() == 11000
							|| mongoException.getCode() == 112
							|| mongoException.hasErrorLabel("TransientTransactionError")))) {
				throw new OrderCreationException.IdempotencyWriteConflict(exception);
			}
			throw exception;
		}
	}

	private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
		Throwable current = exception;
		while (current != null) {
			if (type.isInstance(current)) {
				return type.cast(current);
			}
			current = current.getCause();
		}
		return null;
	}
}
