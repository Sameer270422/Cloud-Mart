package com.cloudmart.notification.kafka;

import com.cloudmart.notification.dto.OrderEvent;
import com.cloudmart.notification.store.NotificationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens on the order-events topic and simulates sending a customer
 * notification (email/SMS/push). In a production system this would call
 * an email provider (SES, SendGrid) or a push notification gateway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationStore notificationStore;

    @KafkaListener(topics = "${cloudmart.kafka.order-events-topic}", groupId = "notification-service")
    public void onOrderEvent(OrderEvent event) {
        String message = "Order #%d for user %d confirmed - total $%s"
                .formatted(event.orderId(), event.userId(), event.totalAmount());
        log.info("[NOTIFY] {}", message);
        notificationStore.add(event, message);
    }
}
