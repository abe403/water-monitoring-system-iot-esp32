package com.watermonitor.ingest.adapter;

import com.watermonitor.domain.ingestion.DecodeException;
import com.watermonitor.domain.ingestion.RawBatch;
import com.watermonitor.domain.ports.DeadLetterPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public final class KafkaDeadLetterAdapter implements DeadLetterPort {

    private static final String DLQ_TOPIC = "telemetry.dlq.v1";

    private final KafkaTemplate<String, byte[]> kafka;

    public KafkaDeadLetterAdapter(KafkaTemplate<String, byte[]> telemetryKafkaTemplate) {
        this.kafka = telemetryKafkaTemplate;
    }

    @Override
    public void publish(RawBatch batch, DecodeException reason) {
        String key = batch.deviceId().value();
        String envelope = """
                {"device":"%s","wire_version":%d,"received_at":"%s","reason":"%s","payload_b64":"%s"}"""
                .formatted(
                        key,
                        batch.wireFormat().version(),
                        batch.receivedAt(),
                        reason.getMessage().replace("\"", "'"),
                        Base64.getEncoder().encodeToString(batch.payload()));

        kafka.send(DLQ_TOPIC, key, envelope.getBytes(StandardCharsets.UTF_8));
    }
}
