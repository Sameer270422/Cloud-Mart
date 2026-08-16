package com.cloudmart.order.kafka;

import com.cloudmart.order.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${cloudmart.kafka.order-events-topic}")
    private String topic;

    public void publishOrderCreated(OrderEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.orderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order event for order {}", event.orderId(), ex);
                    } else {
                        log.info("Published OrderEvent for order {} to topic {}", event.orderId(), topic);
                    }
                });
    }
}
