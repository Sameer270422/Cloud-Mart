package com.cloudmart.notification.kafka;

import com.cloudmart.notification.dto.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Retry + dead-letter handling for the order-events consumer. Without this,
 * a message that fails to deserialize or process (poison pill) gets retried
 * forever by the default Kafka error handling and blocks the partition, or
 * is silently dropped depending on config - neither is acceptable.
 *
 * On failure, a record is retried twice (1s apart) then published to
 * "order-events.DLT" so it can be inspected/replayed instead of lost.
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // A failed record can reach the recoverer in two different shapes: raw
    // bytes (ErrorHandlingDeserializer couldn't even build an OrderEvent)
    // or an already-deserialized OrderEvent (deserialization succeeded but
    // the listener itself threw). DelegatingByTypeSerializer picks the
    // right delegate for whichever shape actually shows up at publish time.
    @SuppressWarnings("unchecked")
    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate() {
        Map<Class<?>, Serializer<?>> delegates = Map.of(
                byte[].class, new ByteArraySerializer(),
                OrderEvent.class, new JsonSerializer<>());
        var valueSerializer = new DelegatingByTypeSerializer(delegates);
        var keySerializer = (Serializer<Object>) (Serializer<?>) new StringSerializer();

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        var producerFactory = new DefaultKafkaProducerFactory<Object, Object>(
                producerProps, keySerializer, valueSerializer);
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
        var backOff = new FixedBackOff(1000L, 2L);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry {} failed for order-event at offset {}: {}",
                        deliveryAttempt, record.offset(), ex.getMessage()));
        return handler;
    }
}
