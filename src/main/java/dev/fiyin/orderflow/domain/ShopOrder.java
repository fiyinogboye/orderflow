package dev.fiyin.orderflow.domain;

import java.time.OffsetDateTime;

public record ShopOrder(String id, String username, long productId, int quantity,
                        long totalCents, Scenario scenario, String status,
                        int attempts, int failures, OffsetDateTime createdAt) {}
