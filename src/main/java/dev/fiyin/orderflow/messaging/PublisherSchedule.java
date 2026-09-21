package dev.fiyin.orderflow.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="orderflow.scheduling", havingValue="true", matchIfMissing=true)
public class PublisherSchedule {
    private final OutboxPublisher publisher;
    public PublisherSchedule(OutboxPublisher publisher) { this.publisher = publisher; }
    @Scheduled(fixedDelayString="${orderflow.poll-ms}")
    public void tick() { publisher.publishReady(); }
}
