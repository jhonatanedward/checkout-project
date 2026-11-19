package dev.edward.ecommerce.checkout_service.infrastructure.rest;

import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import dev.edward.ecommerce.checkout_service.application.order.OrderCreationException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MissingRequestHeaderException.class, ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class, OrderCreationException.Validation.class })
	ResponseEntity<ErrorResponse> badRequest(Exception exception) {
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Order creation validation failed",
				Map.of("reason", "Request fields are missing or invalid"));
	}

	@ExceptionHandler(OrderCreationException.ProductNotFound.class)
	ResponseEntity<ErrorResponse> productNotFound(OrderCreationException.ProductNotFound exception) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_FOUND", "Product not found in trusted catalog",
				Map.of("retryable", false));
	}

	@ExceptionHandler(OrderCreationException.ProductInactive.class)
	ResponseEntity<ErrorResponse> productInactive(OrderCreationException.ProductInactive exception) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_INACTIVE", "Product is inactive",
				Map.of("retryable", false));
	}

	@ExceptionHandler(OrderCreationException.InvalidProductData.class)
	ResponseEntity<ErrorResponse> invalidProduct(OrderCreationException.InvalidProductData exception) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRODUCT_DATA", "Product data is invalid",
				Map.of("retryable", false));
	}

	@ExceptionHandler(OrderCreationException.InvalidProductPrice.class)
	ResponseEntity<ErrorResponse> invalidProductPrice(OrderCreationException.InvalidProductPrice exception) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRODUCT_PRICE", "Product price is invalid",
				Map.of("retryable", false));
	}

	@ExceptionHandler(OrderCreationException.IdempotencyConflict.class)
	ResponseEntity<ErrorResponse> idempotencyConflict(OrderCreationException.IdempotencyConflict exception) {
		return response(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
				"Idempotency key already used for a different request within 24 hours",
				Map.of("conflictingOrderId", exception.conflictingOrderId()));
	}

	@ExceptionHandler(OrderCreationException.NotFound.class)
	ResponseEntity<ErrorResponse> orderNotFound(OrderCreationException.NotFound exception) {
		return response(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order does not exist",
				Map.of("orderId", exception.orderId()));
	}

	@ExceptionHandler(OrderCreationException.CatalogUnavailable.class)
	ResponseEntity<ErrorResponse> catalogUnavailable(OrderCreationException.CatalogUnavailable exception) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_DATA_UNAVAILABLE",
				"Trusted product data is temporarily unavailable", Map.of("retryable", true));
	}

	@ExceptionHandler(OrderCreationException.Retryable.class)
	ResponseEntity<ErrorResponse> retryable(OrderCreationException.Retryable exception) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_CREATION_RETRYABLE",
				"Order creation outcome could not be confirmed; retry with the same idempotency key",
				Map.of("retryable", true));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception exception) {
		LOGGER.error("Unexpected checkout request failure", exception);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
				"Order creation failed due to an unexpected error", Map.of("retryable", true));
	}

	private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
			Map<String, Object> details) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message, details));
	}

	public record ErrorResponse(String error, String message, Map<String, Object> details) {
	}
}
