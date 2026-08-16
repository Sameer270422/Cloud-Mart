package com.cloudmart.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to the "order-events" Kafka topic whenever an order
 * transitions state. Consumed by notification-service (and, in FraudShield,
 * by the fraud-detection pipeline).
 */
public record OrderEvent(
        Long orderId,
        Long userId,
        String status,
        BigDecimal totalAmount,
        Instant occurredAt
) {
    public static OrderEvent created(Long orderId, Long userId, BigDecimal totalAmount) {
        return new OrderEvent(orderId, userId, "CREATED", totalAmount, Instant.now());
    }
}
