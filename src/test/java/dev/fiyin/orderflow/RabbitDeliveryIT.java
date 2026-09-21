package dev.fiyin.orderflow;

import dev.fiyin.orderflow.data.ShopRepository;
import dev.fiyin.orderflow.domain.*;
import dev.fiyin.orderflow.messaging.*;
import dev.fiyin.orderflow.service.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties={"orderflow.scheduling=false","orderflow.retry-delay-ms=0"})
@ActiveProfiles("infra")
@Testcontainers
class RabbitDeliveryIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
        registry.add("spring.rabbitmq.host",rabbit::getHost);
        registry.add("spring.rabbitmq.port",rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username",rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password",rabbit::getAdminPassword);
    }
    @Autowired CheckoutService checkout;
    @Autowired ShopRepository shop;
    @Autowired OutboxPublisher publisher;
    @Autowired RabbitTransport transport;
    @Autowired RabbitTemplate template;

    @Test void realBrokerDeliversARetryWithoutASecondCharge() throws Exception {
        var order = checkout.checkout("student",UUID.randomUUID().toString(),new CheckoutRequest(1,1,Scenario.CHARGE_THEN_TIMEOUT));
        String firstEvent = shop.ready().get(0).id();
        publisher.publishReady();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(shop.order(order.id(),false).attempts()).isEqualTo(1));
        publisher.publishReady();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(shop.order(order.id(),false).status()).isEqualTo("PAID"));
        transport.send(firstEvent);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(shop.metrics().charges()).isEqualTo(1);
            assertThat(shop.order(order.id(),false).attempts()).isEqualTo(2);
        });
    }
    @Test void poisonMessageEndsUpInTheDeadLetterQueue() throws Exception {
        transport.send("unknown-event-id");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(template.receiveAndConvert("orderflow.dead.queue")).isEqualTo("unknown-event-id"));
    }
}
