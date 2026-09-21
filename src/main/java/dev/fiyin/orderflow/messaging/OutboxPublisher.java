package dev.fiyin.orderflow.messaging;

import dev.fiyin.orderflow.data.ShopRepository;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final ShopRepository shop;
    private final PaymentTransport transport;
    public OutboxPublisher(ShopRepository shop, PaymentTransport transport) {
        this.shop = shop;
        this.transport = transport;
    }
    public void publishReady() {
        for (var event : shop.ready()) {
            try {
                transport.send(event.id());
                // A crash before this line can cause a duplicate. The worker must tolerate that.
                shop.sent(event.id());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Outbox event {} remains unsent: {}", event.id(), exception.getClass().getSimpleName());
                break; // Don't hammer an unavailable broker with the whole batch.
            }
        }
    }
}
