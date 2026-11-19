package dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import dev.edward.ecommerce.checkout_service.application.port.out.OrderRepository;
import dev.edward.ecommerce.checkout_service.domain.order.Order;
import dev.edward.ecommerce.checkout_service.domain.order.OrderId;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OrderDocument;

@Repository
public class MongoOrderRepositoryAdapter implements OrderRepository {

	private final MongoTemplate mongoTemplate;

	public MongoOrderRepositoryAdapter(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Order save(Order order) {
		this.mongoTemplate.save(OrderDocument.from(order));
		return order;
	}

	@Override
	public Optional<Order> findById(OrderId orderId) {
		return Optional.ofNullable(this.mongoTemplate.findById(orderId.toString(), OrderDocument.class))
				.map(OrderDocument::toDomain);
	}
}
