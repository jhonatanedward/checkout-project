package dev.edward.ecommerce.checkout_service.application.idempotency;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import dev.edward.ecommerce.checkout_service.application.order.CreateOrderCommand;

public final class OrderRequestFingerprint {

	private OrderRequestFingerprint() {
	}

	public static String create(List<CreateOrderCommand.Item> items) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				List<CreateOrderCommand.Item> sorted = items.stream()
						.sorted(Comparator.comparing(CreateOrderCommand.Item::productId)).toList();
				output.writeInt(sorted.size());
				for (CreateOrderCommand.Item item : sorted) {
					byte[] productId = item.productId().getBytes(StandardCharsets.UTF_8);
					output.writeInt(productId.length);
					output.write(productId);
					output.writeInt(item.quantity());
				}
			}
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not normalize order request", exception);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
