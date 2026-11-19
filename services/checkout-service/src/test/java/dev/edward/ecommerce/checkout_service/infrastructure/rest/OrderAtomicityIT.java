package dev.edward.ecommerce.checkout_service.infrastructure.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.edward.ecommerce.checkout_service.infrastructure.persistence.MongoIntegrationTest;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.IdempotencyDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OrderDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OutboxEventDocument;

@Testcontainers(disabledWithoutDocker = true)
class OrderAtomicityIT extends MongoIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void orderWriteFailureRollsBackAndCanBeRetried() throws Exception {
		this.testOrderRepository.failNextSave();

		create("order-write-failure").andExpect(status().isInternalServerError());
		assertCounts(0, 0, 0);

		assertEquals(201, create("order-write-failure").andReturn().getResponse().getStatus());
		assertCounts(1, 1, 1);
	}

	@Test
	void outboxWriteFailureRollsBackAndCanBeRetried() throws Exception {
		this.testOutboxRepository.failNextSave();

		create("outbox-write-failure").andExpect(status().isInternalServerError());
		assertCounts(0, 0, 0);

		assertEquals(201, create("outbox-write-failure").andReturn().getResponse().getStatus());
		assertCounts(1, 1, 1);
	}

	@Test
	void unknownCommittedOutcomeIsResolvedBySameKeyReplay() throws Exception {
		this.testTransaction.simulateUnknownAfterCommit();

		create("unknown-committed").andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.details.retryable").value(true));
		assertCounts(1, 1, 1);
		MvcResult replay = create("unknown-committed").andExpect(status().isOk()).andReturn();
		assertCounts(1, 1, 1);
		assertEquals(orderId(replay), orderId(create("unknown-committed").andReturn()));
	}

	@Test
	void unknownUncommittedOutcomeCreatesOneOrderOnRetry() throws Exception {
		this.testTransaction.simulateUnknownBeforeCommit();

		create("unknown-uncommitted").andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.details.retryable").value(true));
		assertCounts(0, 0, 0);

		assertEquals(201, create("unknown-uncommitted").andReturn().getResponse().getStatus());
		assertCounts(1, 1, 1);
	}

	private ResultActions create(String key) throws Exception {
		return this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body()));
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

	private static String body() {
		return "{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}";
	}
}
