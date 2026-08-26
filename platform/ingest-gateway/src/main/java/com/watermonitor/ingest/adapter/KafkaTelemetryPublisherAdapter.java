package com.watermonitor.ingest.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watermonitor.domain.ports.PublishReceipt;
import com.watermonitor.domain.ports.TelemetryPublisherPort;
import com.watermonitor.domain.telemetry.TelemetryRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;

/**
 * The adapter half of {@link TelemetryPublisherPort}. Its contract is
 * inherited verbatim from the port's Javadoc: do not complete the returned
 * stage until Kafka has durably replicated the record. That guarantee comes
 * entirely from {@code DurableKafkaProducerConfig} (acks=all, idempotent
 * producer) plus the topic's {@code min.insync.replicas=2} — this class adds
 * no durability logic of its own, it only must not accidentally weaken what
 * the producer config already provides (e.g. by wrapping the send in
 * fire-and-forget code, or by completing on submission instead of on the
 * producer's send future).
 */
@Component
public final class KafkaTelemetryPublisherAdapter implements TelemetryPublisherPort {

    private static final String TOPIC = "telemetry.raw.v1";

    private final KafkaTemplate<String, byte[]> kafka;
    private final ObjectMapper json;

    public KafkaTelemetryPublisherAdapter(KafkaTemplate<String, byte[]> telemetryKafkaTemplate, ObjectMapper json) {
        this.kafka = telemetryKafkaTemplate;
        this.json = json;
    }

    @Override
    public CompletionStage<PublishReceipt> publish(TelemetryRecord record) {
        byte[] payload;
        try {
            payload = json.writeValueAsBytes(TelemetryEventDto.from(record));
        } catch (Exception e) {
            // A serialization failure here is a programming error (the DTO
            // mapping is unconditional), not a transient condition — fail
            // fast rather than silently dropping the record.
            throw new IllegalStateException("failed to serialize " + record.idempotencyKey(), e);
        }

        String key = record.deviceId().value();

        return kafka.send(TOPIC, key, payload)
                .thenApply(result -> new PublishReceipt(
                        record.idempotencyKey(),
                        result.getRecordMetadata().offset(),
                        result.getRecordMetadata().partition()));
    }
}
