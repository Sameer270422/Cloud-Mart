package com.cloudmart.notification.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A message that fails to deserialize as OrderEvent (poison pill) must not
 * get stuck retrying the same offset forever, nor be silently dropped - it
 * should end up on the order-events.DLT topic after the configured retries.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "order-events.DLT"})
class OrderEventDlqTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void malformedPayloadEndsUpOnTheDeadLetterTopicAfterRetries() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);
        KafkaTemplate<String, String> producer =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
        producer.send("order-events", "key-1", "not-valid-json");

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("dlq-test-group", "true", embeddedKafkaBroker);
        consumerProps.put("key.deserializer", ByteArrayDeserializer.class);
        consumerProps.put("value.deserializer", ByteArrayDeserializer.class);
        consumerProps.put("auto.offset.reset", "earliest");

        try (Consumer<byte[], byte[]> dltConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "order-events.DLT");

            ConsumerRecord<byte[], byte[]> dltRecord =
                    KafkaTestUtils.getSingleRecord(dltConsumer, "order-events.DLT", Duration.ofSeconds(15));

            assertThat(dltRecord).isNotNull();
            assertThat(new String(dltRecord.value())).isEqualTo("not-valid-json");
        }
    }
}
