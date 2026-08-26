package com.watermonitor.ingest;

import com.watermonitor.ingest.config.DurableKafkaProducerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class DurableKafkaProducerConfigTest {

    @Test
    void producerUsesTheWireContractSerializersAndDurabilitySettings() {
        var factory = (DefaultKafkaProducerFactory<String, byte[]>)
                new DurableKafkaProducerConfig().telemetryProducerFactory(new KafkaProperties(), "zstd");
        var properties = factory.getConfigurationProperties();

        assertThat(properties)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    }
}
