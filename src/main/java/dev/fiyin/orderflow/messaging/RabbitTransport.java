package dev.fiyin.orderflow.messaging;

import dev.fiyin.orderflow.service.PaymentWorker;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="orderflow.transport", havingValue="rabbit")
public class RabbitTransport implements PaymentTransport {
    private final RabbitTemplate rabbit;
    private final PaymentWorker worker;
    public RabbitTransport(RabbitTemplate rabbit, PaymentWorker worker) {
        this.rabbit = rabbit;
        this.worker = worker;
    }
    public void send(String eventId) throws Exception {
        var receipt = new CorrelationData(UUID.randomUUID().toString());
        rabbit.convertAndSend("orderflow.payments", "pay", eventId, receipt);
        var confirm = receipt.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || receipt.getReturned() != null)
            throw new IllegalStateException("Broker did not safely route the event");
    }
    @RabbitListener(queues="orderflow.payments.queue")
    public void receive(String eventId) {
        // Spring acknowledges after this method returns, which is after the DB transaction commits.
        worker.process(eventId);
    }
}
