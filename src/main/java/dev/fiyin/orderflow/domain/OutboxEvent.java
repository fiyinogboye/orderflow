package dev.fiyin.orderflow.domain;

public record OutboxEvent(String id, String orderId, int attempt) {}
