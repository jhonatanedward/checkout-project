package dev.edward.ecommerce.checkout_service.infrastructure.rest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(@NotEmpty List<@NotNull @Valid Item> items) {

	public record Item(@NotBlank String productId, @NotNull @Positive Integer quantity) {
	}
}
