package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import dev.edward.ecommerce.checkout_service.domain.idempotency.IdempotencyRecord;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;

@Document(collection = "idempotency_keys")
public record IdempotencyDocument(@Id String id, @Indexed(unique = true) String key, String contentHash,
		String orderId, Instant createdAt, @Indexed(expireAfter = "0s") Instant expiresAt) {

	public static IdempotencyDocument from(IdempotencyRecord record) {
		String key = record.key();
		return new IdempotencyDocument(key, key, record.contentHash(), record.orderId().toString(), record.createdAt(),
				record.expiresAt());
	}

	public IdempotencyRecord toDomain() {
		return new IdempotencyRecord(this.key, this.contentHash, OrderId.from(this.orderId), this.createdAt,
				this.expiresAt);
	}
}
