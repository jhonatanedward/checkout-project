package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import dev.edward.ecommerce.checkout_service.application.port.out.OutboxRepository;
import dev.edward.ecommerce.checkout_service.domain.event.OrderCreatedEvent;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OutboxEventDocument;

@Repository
public class MongoOutboxRepositoryAdapter implements OutboxRepository {

	private final MongoTemplate mongoTemplate;

	public MongoOutboxRepositoryAdapter(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void save(OrderCreatedEvent event) {
		this.mongoTemplate.insert(OutboxEventDocument.from(event));
	}
}
