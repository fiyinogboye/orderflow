package dev.fiyin.orderflow.domain;

import jakarta.validation.constraints.*;

public record CheckoutRequest(@Positive long productId,
                              @Min(1) @Max(10) int quantity,
                              @NotNull Scenario scenario) {
    // The same key is only allowed to repeat the same request.
    public String signature() {
        return productId + ":" + quantity + ":" + scenario;
    }
}
