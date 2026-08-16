package com.cloudmart.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        Long orderId,
        Long userId,
        String status,
        BigDecimal totalAmount,
        Instant occurredAt
) {}
