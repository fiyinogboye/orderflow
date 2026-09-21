package dev.fiyin.orderflow.service;

import dev.fiyin.orderflow.data.ShopRepository;
import dev.fiyin.orderflow.domain.ShopOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryService {
    private final ShopRepository shop;
    public RecoveryService(ShopRepository shop) { this.shop = shop; }
    @Transactional
    public ShopOrder recover(String id) {
        ShopOrder order = shop.order(id, true);
        if (!order.status().equals("DEAD")) throw new ShopException(409, "Only DEAD orders need payment recovery.");
        // This button means our fake payment provider is healthy again.
        shop.recover(id);
        shop.enqueue(id, order.attempts() + 1, 0);
        shop.note(id, "Operator restored the fake provider. Payment requeued with the same order ID.");
        return shop.order(id, false);
    }
    @Transactional
    public ShopOrder cancel(String id) {
        ShopOrder order = shop.order(id, true);
        if (!order.status().equals("DEAD")) throw new ShopException(409, "Only DEAD orders can be cancelled here.");
        if (shop.charged(id)) throw new ShopException(409, "A payment receipt exists. Recover this order instead.");
        shop.state(id, "CANCELLED", order.attempts(), order.failures());
        shop.release(order);
        shop.note(id, "Operator checked the fake receipt ledger, cancelled the order, and returned stock.");
        return shop.order(id, false);
    }
    @Transactional
    public void redeliver(String id) {
        ShopOrder order = shop.order(id, true);
        if (!order.status().equals("PENDING")) throw new ShopException(409, "Only pending work can be redelivered.");
        shop.redeliver(id, order.attempts() + 1);
        shop.note(id, "Operator requested redelivery of the outstanding attempt.");
    }
}
