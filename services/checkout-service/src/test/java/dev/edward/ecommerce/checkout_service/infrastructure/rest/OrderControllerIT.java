package dev.edward.ecommerce.checkout_service.infrastructure.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.edward.ecommerce.checkout_service.domain.product.TrustedProduct;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.MongoIntegrationTest;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OrderDocument;
import dev.edward.ecommerce.checkout_service.infrastructure.persistence.mongo.document.OutboxEventDocument;

@Testcontainers(disabledWithoutDocker = true)
class OrderControllerIT extends MongoIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void createsAndRetrievesAnOrderSnapshot() throws Exception {
		String response = this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "order-http-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":2},{\"productId\":\"WIDGET_B\",\"quantity\":1}]}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(99.99))
				.andExpect(jsonPath("$.currency").value("USD")).andReturn().getResponse().getContentAsString();
		JsonNode created = this.objectMapper.readTree(response);

		this.testCatalog.put(new TrustedProduct("WIDGET_A", "Renamed", new java.math.BigDecimal("1.00"), "USD", true));
		this.mockMvc.perform(get("/api/v1/orders/{orderId}", created.get("id").asText()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productName").value("Widget A"))
				.andExpect(jsonPath("$.items[0].unitPrice").value(29.99))
				.andExpect(jsonPath("$.totalAmount").value(99.99));

		org.junit.jupiter.api.Assertions.assertEquals(1, this.mongoTemplate.count(
				new org.springframework.data.mongodb.core.query.Query(), OrderDocument.class));
		org.junit.jupiter.api.Assertions.assertEquals(1, this.mongoTemplate.count(
				new org.springframework.data.mongodb.core.query.Query(), OutboxEventDocument.class));
	}

	@Test
	void mapsMalformedRequestsAndProductFailuresToContractStatuses() throws Exception {
		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "empty")
				.contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "duplicate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1},{\"productId\":\"WIDGET_A\",\"quantity\":2}]}"))
				.andExpect(status().isBadRequest());

		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "unknown")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"NO_SUCH_PRODUCT\",\"quantity\":1}]}"))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));

		this.testCatalog.put(new TrustedProduct("INACTIVE", "Inactive widget",
				new java.math.BigDecimal("9.00"), "USD", false));
		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "inactive")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"INACTIVE\",\"quantity\":1}]}"))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("PRODUCT_INACTIVE"));

		this.testCatalog.put(new TrustedProduct("BROKEN", "No price", null, "USD", true));
		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "invalid-price")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"BROKEN\",\"quantity\":1}]}"))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("INVALID_PRODUCT_PRICE"));

		this.testCatalog.setAvailable(false);
		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "unavailable")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("PRODUCT_DATA_UNAVAILABLE"));

		org.junit.jupiter.api.Assertions.assertEquals(0, this.mongoTemplate.count(
				new org.springframework.data.mongodb.core.query.Query(), OrderDocument.class));

		this.testCatalog.setAvailable(true);
		this.mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "unavailable")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":\"WIDGET_A\",\"quantity\":1}]}"))
				.andExpect(status().isCreated());
		org.junit.jupiter.api.Assertions.assertEquals(1, this.mongoTemplate.count(
				new org.springframework.data.mongodb.core.query.Query(), OrderDocument.class));
	}

	@Test
	void returnsNotFoundForUnknownOrderId() throws Exception {
		this.mockMvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000001"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
	}
}
