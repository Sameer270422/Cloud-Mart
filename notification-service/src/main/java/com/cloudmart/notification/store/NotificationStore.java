package com.cloudmart.notification.store;

import com.cloudmart.notification.dto.OrderEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * In-memory ring buffer of recent notifications so the /api/notifications
 * endpoint has something to show without needing a database for this service.
 */
@Component
public class NotificationStore {

    private static final int MAX_SIZE = 200;
    private final LinkedList<NotificationRecord> notifications = new LinkedList<>();

    public synchronized void add(OrderEvent event, String message) {
        notifications.addFirst(new NotificationRecord(event.orderId(), event.userId(), message, Instant.now()));
        if (notifications.size() > MAX_SIZE) {
            notifications.removeLast();
        }
    }

    public synchronized List<NotificationRecord> recent() {
        return Collections.unmodifiableList(new LinkedList<>(notifications));
    }

    public record NotificationRecord(Long orderId, Long userId, String message, Instant sentAt) {}
}
