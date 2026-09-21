package dev.fiyin.orderflow.service;

import dev.fiyin.orderflow.data.ShopRepository;
import dev.fiyin.orderflow.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWorker {
    private final ShopRepository shop;
    private final long retryDelay;
    public PaymentWorker(ShopRepository shop, @Value("${orderflow.retry-delay-ms}") long retryDelay) {
        this.shop = shop;
        this.retryDelay = retryDelay;
    }

    @Transactional
    public void process(String eventId) {
        OutboxEvent event = shop.event(eventId).orElseThrow(() -> new IllegalArgumentException("Unknown payment event"));
        ShopOrder order = shop.order(event.orderId(), true);
        // Delivery can happen twice. Only the next expected attempt may change anything.
        if (!order.status().equals("PENDING") || event.attempt() != order.attempts() + 1) return;
        int attempt = event.attempt();
        shop.note(order.id(), "Starting payment attempt " + attempt + ".");

        // A lost reply does not mean the charge failed. Check the fake provider's receipt first.
        if (shop.charged(order.id())) {
            paid(order, attempt, "Found the earlier receipt. No second charge.");
            return;
        }
        if (order.scenario() == Scenario.DECLINE) {
            shop.state(order.id(), "DECLINED", attempt, order.failures());
            shop.release(order);
            shop.note(order.id(), "Payment declined. Reserved stock returned once.");
            return;
        }
        if (order.scenario() == Scenario.CHARGE_THEN_TIMEOUT) {
            shop.charge(order);
            retry(order, attempt, "Fake provider charged, but its reply was lost.");
            return;
        }
        if (order.scenario() == Scenario.ALWAYS_TIMEOUT ||
            (order.scenario() == Scenario.TIMEOUT_ONCE && order.failures() == 0)) {
            retry(order, attempt, "Fake provider timed out.");
            return;
        }
        shop.charge(order);
        paid(order, attempt, "Payment accepted. Order complete.");
    }

    private void paid(ShopOrder order, int attempt, String message) {
        shop.state(order.id(), "PAID", attempt, order.failures());
        shop.note(order.id(), message);
    }
    private void retry(ShopOrder order, int attempt, String reason) {
        int failures = order.failures() + 1;
        if (failures >= 3) {
            shop.state(order.id(), "DEAD", attempt, failures);
            shop.note(order.id(), reason + " Retry budget exhausted. Stock stays reserved for review.");
        } else {
            shop.state(order.id(), "PENDING", attempt, failures);
            long delay = retryDelay * (1L << (failures - 1));
            shop.enqueue(order.id(), attempt + 1, delay);
            shop.note(order.id(), reason + " Retry saved for " + delay + " ms later.");
        }
    }
}
