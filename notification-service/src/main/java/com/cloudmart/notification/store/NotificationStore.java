package com.cloudmart.notification.store;

import com.cloudmart.notification.dto.OrderEvent;
import com.cloudmart.notification.model.Notification;
import com.cloudmart.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Persists recent notifications so /api/notifications survives a restart
 * and can be scaled beyond a single instance - it used to be an in-memory
 * ring buffer, which lost everything on restart and couldn't be shared
 * across replicas.
 */
@Component
@RequiredArgsConstructor
public class NotificationStore {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void add(OrderEvent event, String message) {
        notificationRepository.save(Notification.builder()
                .orderId(event.orderId())
                .userId(event.userId())
                .message(message)
                .sentAt(Instant.now())
                .build());
    }

    public List<NotificationRecord> recent() {
        return notificationRepository.findTop200ByOrderBySentAtDesc().stream()
                .map(n -> new NotificationRecord(n.getOrderId(), n.getUserId(), n.getMessage(), n.getSentAt()))
                .toList();
    }

    public record NotificationRecord(Long orderId, Long userId, String message, Instant sentAt) {}
}
