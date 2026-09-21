package dev.fiyin.orderflow.messaging;

public interface PaymentTransport {
    void send(String eventId) throws Exception;
}
