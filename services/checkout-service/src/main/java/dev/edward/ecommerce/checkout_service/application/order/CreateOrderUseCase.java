package dev.edward.ecommerce.checkout_service.application.order;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.edward.ecommerce.checkout_service.application.idempotency.OrderRequestFingerprint;
import dev.edward.ecommerce.checkout_service.application.port.out.IdempotencyRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderCreationTransaction;
import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.OutboxRepository;
import dev.edward.ecommerce.checkout_service.application.port.out.TrustedProductCatalog;
import dev.edward.ecommerce.checkout_service.domain.event.OrderCreatedEvent;
import dev.edward.ecommerce.checkout_service.domain.idempotency.IdempotencyRecord;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderItem;
import dev.edward.ecommerce.checkout_service.domain.product.TrustedProduct;

public final class CreateOrderUseCase {

	private static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);

	private static final int MAX_CLAIM_ATTEMPTS = 3;

	private final TrustedProductCatalog catalog;

	private final OrderRepository orderRepository;

	private final OutboxRepository outboxRepository;

	private final IdempotencyRepository idempotencyRepository;

	private final OrderCreationTransaction transaction;

	private final Clock clock;

	public CreateOrderUseCase(TrustedProductCatalog catalog, OrderRepository orderRepository,
			OutboxRepository outboxRepository, IdempotencyRepository idempotencyRepository,
			OrderCreationTransaction transaction, Clock clock) {
		this.catalog = catalog;
		this.orderRepository = orderRepository;
		this.outboxRepository = outboxRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.transaction = transaction;
		this.clock = clock;
	}

	public CreateOrderResult create(CreateOrderCommand command) {
		validate(command);
		String fingerprint = OrderRequestFingerprint.create(command.items());
		Optional<CreateOrderResult> existing = findExisting(command.idempotencyKey(), fingerprint, this.clock.instant());
		if (existing.isPresent()) {
			return existing.get();
		}

		for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
			try {
				return this.transaction.execute(() -> createWithinTransaction(command, fingerprint));
			}
			catch (OrderCreationException.IdempotencyWriteConflict exception) {
				existing = findExisting(command.idempotencyKey(), fingerprint, this.clock.instant());
				if (existing.isPresent()) {
					return existing.get();
				}
			}
		}
		throw new OrderCreationException.Retryable("Could not claim idempotency key after bounded retries");
	}

	private CreateOrderResult createWithinTransaction(CreateOrderCommand command, String fingerprint) {
		Optional<CreateOrderResult> existing = findExisting(command.idempotencyKey(), fingerprint, this.clock.instant());
		if (existing.isPresent()) {
			return existing.get();
		}

		List<OrderItem> items = new ArrayList<>();
		for (CreateOrderCommand.Item requested : command.items()) {
			TrustedProduct product = this.catalog.findById(requested.productId())
					.orElseThrow(() -> new OrderCreationException.ProductNotFound(requested.productId()));
			if (!product.active()) {
				throw new OrderCreationException.ProductInactive(requested.productId());
			}
			if (product.unitPrice() == null || product.unitPrice().signum() <= 0) {
				throw new OrderCreationException.InvalidProductPrice(requested.productId());
			}
			try {
				product.unitPrice().setScale(2, RoundingMode.UNNECESSARY);
			}
			catch (ArithmeticException exception) {
				throw new OrderCreationException.InvalidProductPrice(requested.productId());
			}
			try {
				if (product.id() == null || !product.id().equals(requested.productId()) || product.name() == null
						|| product.name().isBlank()
						|| product.currency() == null || product.currency().isBlank()) {
					throw new IllegalArgumentException("Required product fields are missing");
				}
				items.add(new OrderItem(product.id(), product.name(), requested.quantity(), product.unitPrice(),
						product.currency()));
			}
			catch (IllegalArgumentException exception) {
				throw new OrderCreationException.InvalidProductData(requested.productId(), exception);
			}
		}
		if (items.stream().map(OrderItem::currency).distinct().count() != 1) {
			throw new OrderCreationException.InvalidProductData("catalog-currency");
		}

		Instant createdAt = this.clock.instant();
		Order order = Order.create(command.idempotencyKey(), items.get(0).currency(), items, createdAt);
		this.orderRepository.save(order);
		this.outboxRepository.save(new OrderCreatedEvent(order.id(), createdAt,
				new OrderCreatedEvent.Payload(order.totalAmount(), order.currency(), order.items().size(),
						order.idempotencyKey())));
		this.idempotencyRepository.claim(new IdempotencyRecord(command.idempotencyKey(), fingerprint, order.id(),
				createdAt, createdAt.plus(IDEMPOTENCY_WINDOW)), createdAt);
		return new CreateOrderResult(order, false);
	}

	private Optional<CreateOrderResult> findExisting(String key, String fingerprint, Instant now) {
		Optional<IdempotencyRecord> found = this.idempotencyRepository.findByKey(key);
		if (found.isEmpty() || !found.get().isActiveAt(now)) {
			return Optional.empty();
		}
		IdempotencyRecord record = found.get();
		if (!record.contentHash().equals(fingerprint)) {
			throw new OrderCreationException.IdempotencyConflict(record.orderId().toString());
		}
		Order order = this.orderRepository.findById(record.orderId())
				.orElseThrow(() -> new OrderCreationException.PersistenceState(
						"Committed idempotency record references a missing order"));
		return Optional.of(new CreateOrderResult(order, true));
	}

	private static void validate(CreateOrderCommand command) {
		if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
				|| command.items() == null || command.items().isEmpty()) {
			throw new OrderCreationException.Validation("Idempotency key and at least one item are required");
		}
		Set<String> productIds = new HashSet<>();
		for (CreateOrderCommand.Item item : command.items()) {
			if (item == null || item.productId() == null || item.productId().isBlank() || item.quantity() <= 0) {
				throw new OrderCreationException.Validation("Each item requires a product ID and positive quantity");
			}
			if (!productIds.add(item.productId())) {
				throw new OrderCreationException.Validation("A product cannot appear more than once");
			}
		}
	}
}
