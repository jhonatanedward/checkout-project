package dev.edward.ecommerce.checkout_service;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.edward.ecommerce.checkout_service.infrastructure.persistence.MongoIntegrationTest;

@Testcontainers(disabledWithoutDocker = true)
class CheckoutServiceApplicationTests extends MongoIntegrationTest {

	@Test
	void contextLoads() {
	}

}
