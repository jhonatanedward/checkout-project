package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import dev.edward.ecommerce.checkout_service.application.order.OrderCreationException;
import dev.edward.ecommerce.checkout_service.application.port.out.IdempotencyRepository;
import dev.edward.ecommerce.checkout_service.domain.idempotency.IdempotencyRecord;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.IdempotencyDocument;

@Repository
public class MongoIdempotencyRepositoryAdapter implements IdempotencyRepository {

	private final MongoTemplate mongoTemplate;

	public MongoIdempotencyRepositoryAdapter(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Optional<IdempotencyRecord> findByKey(String key) {
		return Optional.ofNullable(
				this.mongoTemplate.findOne(Query.query(Criteria.where("key").is(key)), IdempotencyDocument.class))
				.map(IdempotencyDocument::toDomain);
	}

	@Override
	public void claim(IdempotencyRecord record, Instant now) {
		IdempotencyDocument document = IdempotencyDocument.from(record);
		Query expiredRecord = Query.query(Criteria.where("key").is(record.key()).and("expiresAt").lte(now));
		IdempotencyDocument replaced = this.mongoTemplate.findAndReplace(expiredRecord, document,
				FindAndReplaceOptions.options());
		if (replaced != null) {
			return;
		}
		try {
			this.mongoTemplate.insert(document);
		}
		catch (DuplicateKeyException exception) {
			throw new OrderCreationException.IdempotencyWriteConflict(exception);
		}
	}
}
