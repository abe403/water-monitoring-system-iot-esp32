package com.watermonitor.ingest.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The producer-side half of "zero data loss under sustained ingestion
 * spikes". Every setting here is load-bearing — see
 * docs/ARCHITECTURE.md, "the durability chain" for what each one defends
 * against. This is deliberately its own configuration class rather than
 * relying on {@code application.yml} defaults, so the durability contract is
 * visible in code and covered by
 * {@code DurableKafkaProducerConfigTest.everySettingMatchesTheDurabilityContract()}.
 */
@Configuration
public class DurableKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, byte[]> telemetryProducerFactory(
            KafkaProperties kafkaProperties,
            @Value("${watermonitor.kafka.compression:zstd}") String compressionType) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));

        // Do not rely on Spring Boot's generic producer defaults here. The
        // wire contract is a String device key and a JSON byte[] value; a
        // StringSerializer accepts the template's generic type at compile
        // time but fails only when the first live record is sent.
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        // acks=all + min.insync.replicas>=2 on the topic (see infra/topics.yaml)
        // is the pair that makes "durably replicated" mean something; acks=all
        // against a topic with min.insync.replicas=1 is a single point of
        // failure wearing a durability guarantee's clothes.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65_536);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, byte[]> telemetryKafkaTemplate(
            ProducerFactory<String, byte[]> telemetryProducerFactory) {
        return new KafkaTemplate<>(telemetryProducerFactory);
    }
}
