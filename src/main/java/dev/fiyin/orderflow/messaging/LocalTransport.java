package dev.fiyin.orderflow.messaging;

import dev.fiyin.orderflow.service.PaymentWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="orderflow.transport", havingValue="local")
public class LocalTransport implements PaymentTransport {
    private final PaymentWorker worker;
    public LocalTransport(PaymentWorker worker) { this.worker = worker; }
    public void send(String eventId) {
        // Easy-start mode: call the worker directly. This is NOT a real message broker.
        worker.process(eventId);
    }
}
