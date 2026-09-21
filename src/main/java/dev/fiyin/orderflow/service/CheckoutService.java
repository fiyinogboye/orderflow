package dev.fiyin.orderflow.service;

import dev.fiyin.orderflow.data.ShopRepository;
import dev.fiyin.orderflow.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
    private final ShopRepository shop;
    public CheckoutService(ShopRepository shop) { this.shop = shop; }

    @Transactional
    public ShopOrder checkout(String username, String key, CheckoutRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new ShopException(400, "Use an Idempotency-Key with 8–80 letters, numbers, dashes or underscores.");
        if (request.quantity() < 1 || request.quantity() > 10 || request.productId() <= 0 || request.scenario() == null)
            throw new ShopException(400, "Choose a product, 1–10 items, and a payment scenario.");
        shop.lockCustomer(username);
        var previous = shop.previous(username, key, request.signature());
        if (previous.isPresent()) return previous.get();

        long price = shop.reserve(request.productId(), request.quantity());
        ShopOrder order = shop.insert(username, key, request, price);
        shop.enqueue(order.id(), 1, 0);
        shop.note(order.id(), "Stock reserved. Payment job saved in the outbox.");
        // All three writes commit together. If one fails, none of them should stay.
        return order;
    }
}
