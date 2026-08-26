package com.watermonitor.api.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Bridges committed enriched readings to each device's STOMP topic. */
@Component
public final class LiveTelemetryPublisher {

    private final SimpMessagingTemplate messaging;
    private final ObjectMapper json;

    public LiveTelemetryPublisher(SimpMessagingTemplate messaging, ObjectMapper json) {
        this.messaging = messaging;
        this.json = json;
    }

    @KafkaListener(
            topics = "telemetry.enriched.v1",
            groupId = "operations-api-live",
            properties = {
                    "auto.offset.reset=latest",
                    "enable.auto.commit=false"
            })
    public void consume(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode payload = json.readTree(record.value());
            String deviceId = payload.get("deviceId").asText();
            messaging.convertAndSend("/topic/telemetry/" + deviceId, payload);
            messaging.convertAndSend("/topic/telemetry", payload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to publish live telemetry at partition " + record.partition() +
                            " offset " + record.offset(), e);
        }
    }
}
