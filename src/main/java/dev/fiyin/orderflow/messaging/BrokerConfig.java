package dev.fiyin.orderflow.messaging;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name="orderflow.transport", havingValue="rabbit")
public class BrokerConfig {
    @Bean
    Declarables topology() {
        DirectExchange exchange = new DirectExchange("orderflow.payments", true, false);
        DirectExchange dead = new DirectExchange("orderflow.dead", true, false);
        Queue queue = QueueBuilder.durable("orderflow.payments.queue")
            .deadLetterExchange("orderflow.dead").deadLetterRoutingKey("dead").build();
        Queue deadQueue = QueueBuilder.durable("orderflow.dead.queue").build();
        return new Declarables(exchange, dead, queue, deadQueue,
            BindingBuilder.bind(queue).to(exchange).with("pay"),
            BindingBuilder.bind(deadQueue).to(dead).with("dead"));
    }
}
