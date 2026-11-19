package dev.edward.ecommerce.checkout_service.application.order;

public class OrderCreationException extends RuntimeException {

	public OrderCreationException(String message) {
		super(message);
	}

	public OrderCreationException(String message, Throwable cause) {
		super(message, cause);
	}

	public static final class Validation extends OrderCreationException {
		public Validation(String message) {
			super(message);
		}
	}

	public static final class ProductNotFound extends OrderCreationException {
		public ProductNotFound(String productId) {
			super("Product was not found: " + productId);
		}
	}

	public static final class ProductInactive extends OrderCreationException {
		public ProductInactive(String productId) {
			super("Product is inactive: " + productId);
		}
	}

	public static final class InvalidProductData extends OrderCreationException {
		public InvalidProductData(String productId, Throwable cause) {
			super("Product data is invalid: " + productId, cause);
		}

		public InvalidProductData(String productId) {
			super("Product data is invalid: " + productId);
		}
	}

	public static final class InvalidProductPrice extends OrderCreationException {
		public InvalidProductPrice(String productId) {
			super("Product price is invalid: " + productId);
		}
	}

	public static final class CatalogUnavailable extends OrderCreationException {
		public CatalogUnavailable(String message) {
			super(message);
		}

		public CatalogUnavailable(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static final class IdempotencyConflict extends OrderCreationException {
		private final String conflictingOrderId;

		public IdempotencyConflict(String conflictingOrderId) {
			super("Idempotency key is active for different request content");
			this.conflictingOrderId = conflictingOrderId;
		}

		public String conflictingOrderId() {
			return conflictingOrderId;
		}
	}

	public static final class IdempotencyWriteConflict extends OrderCreationException {
		public IdempotencyWriteConflict(Throwable cause) {
			super("Idempotency key claim conflicted with another transaction", cause);
		}
	}

	public static final class Retryable extends OrderCreationException {
		public Retryable(String message) {
			super(message);
		}
	}

	public static final class NotFound extends OrderCreationException {
		private final String orderId;

		public NotFound(String orderId) {
			super("Order does not exist: " + orderId);
			this.orderId = orderId;
		}

		public String orderId() {
			return orderId;
		}
	}

	public static final class PersistenceState extends OrderCreationException {
		public PersistenceState(String message) {
			super(message);
		}
	}
}
