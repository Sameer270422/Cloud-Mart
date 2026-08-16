package com.cloudmart.notification.store;

import com.cloudmart.notification.dto.OrderEvent;
import com.cloudmart.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events"})
class NotificationStoreTest {

    @Autowired
    private NotificationStore notificationStore;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void recordedNotificationsArePersistedAndReturnedNewestFirst() {
        notificationStore.add(
                new OrderEvent(1L, 10L, "CREATED", new BigDecimal("19.99"), Instant.now()),
                "Order #1 confirmed");
        notificationStore.add(
                new OrderEvent(2L, 10L, "CREATED", new BigDecimal("29.99"), Instant.now().plusSeconds(1)),
                "Order #2 confirmed");

        assertThat(notificationRepository.count()).isEqualTo(2);

        List<NotificationStore.NotificationRecord> recent = notificationStore.recent();

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).orderId()).isEqualTo(2L);
        assertThat(recent.get(1).orderId()).isEqualTo(1L);
    }
}
