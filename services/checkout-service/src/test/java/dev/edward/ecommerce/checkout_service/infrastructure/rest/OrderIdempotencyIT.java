package dev.edward.ecommerce.checkout_service.infrastructure.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.edward.ecommerce.checkout_service.infrastructure.persistence.MongoIntegrationTest;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.IdempotencyDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OrderDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OutboxEventDocument;

@Testcontainers(disabledWithoutDocker = true)
class OrderIdempotencyIT extends MongoIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void concurrentSameContentRequestsCreateOneOrderAndEvent() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<MvcResult> first = executor.submit(() -> concurrentPost(ready, start, "same-key",
					"{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}"));
			Future<MvcResult> second = executor.submit(() -> concurrentPost(ready, start, "same-key",
					"{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}"));
			ready.await();
			start.countDown();
			MvcResult firstResult = first.get();
			MvcResult secondResult = second.get();

			assertEquals(1, (firstResult.getResponse().getStatus() == 201 ? 1 : 0)
					+ (secondResult.getResponse().getStatus() == 201 ? 1 : 0));
			assertEquals(1, (firstResult.getResponse().getStatus() == 200 ? 1 : 0)
					+ (secondResult.getResponse().getStatus() == 200 ? 1 : 0));
			assertEquals(orderId(firstResult), orderId(secondResult));
			assertCounts(1, 1, 1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentDifferentContentForOneKeyReturnsConflict() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<MvcResult> first = executor.submit(() -> concurrentPost(ready, start, "conflict-key",
					"{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}"));
			Future<MvcResult> second = executor.submit(() -> concurrentPost(ready, start, "conflict-key",
					"{\"items\":[{\"productId\":\"WIDGET_B\",\"quantity\":1}]}"));
			ready.await();
			start.countDown();
			int firstStatus = first.get().getResponse().getStatus();
			int secondStatus = second.get().getResponse().getStatus();

			assertEquals(1, (firstStatus == 201 ? 1 : 0) + (secondStatus == 201 ? 1 : 0));
			assertEquals(1, (firstStatus == 409 ? 1 : 0) + (secondStatus == 409 ? 1 : 0));
			assertCounts(1, 1, 1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void reusesExpiredKeyBeforeMongoTtlCleanup() throws Exception {
		MvcResult first = create("expiry-key", "WIDGET_A");
		String originalId = orderId(first);
		Instant expiresAt = this.mongoTemplate.findAll(IdempotencyDocument.class).get(0).expiresAt();
		this.testClock.advance(Duration.ofHours(24));

		assertEquals(true, expiresAt.isAfter(Instant.now()));
		assertEquals(1, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				IdempotencyDocument.class));
		MvcResult second = create("expiry-key", "WIDGET_A");

		assertEquals(201, second.getResponse().getStatus());
		assertNotEquals(originalId, orderId(second));
		assertCounts(2, 2, 1);
	}

	@Test
	void confirmedRollbackDoesNotPersistAnIdempotencyWindow() throws Exception {
		this.testOutboxRepository.failNextSave();

		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "rollback-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}"))
				.andExpect(status().isInternalServerError());

		assertCounts(0, 0, 0);
		assertEquals(201, create("rollback-key", "WIDGET_A").getResponse().getStatus());
		assertCounts(1, 1, 1);
	}

	private MvcResult concurrentPost(CountDownLatch ready, CountDownLatch start, String key, String body)
			throws Exception {
		ready.countDown();
		start.await();
		return this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	private MvcResult create(String key, String productId) throws Exception {
		return this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":1}]}"))
				.andReturn();
	}

	private String orderId(MvcResult result) throws Exception {
		JsonNode body = this.objectMapper.readTree(result.getResponse().getContentAsString());
		return body.get("id").asText();
	}

	private void assertCounts(long orders, long events, long idempotencyRecords) {
		assertEquals(orders, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OrderDocument.class));
		assertEquals(events, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				OutboxEventDocument.class));
		assertEquals(idempotencyRecords, this.mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
				IdempotencyDocument.class));
	}

}
