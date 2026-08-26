package com.watermonitor.sink.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public final class TelemetryKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaConsumer.class);

    private final ReadingJdbcRepository repo;
    private final ObjectMapper json;

    public TelemetryKafkaConsumer(ReadingJdbcRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @KafkaListener(
            topics = "telemetry.enriched.v1",
            groupId = "timescale-sink",
            properties = {
                    "auto.offset.reset=earliest",
                    "enable.auto.commit=false"
            })
    public void consume(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode node = json.readTree(record.value());

            repo.upsert(
                    node.get("deviceId").asText(),
                    node.get("bootId").asLong(),
                    node.get("seq").asLong(),
                    Instant.parse(node.get("observedAt").asText()),
                    Instant.parse(node.get("receivedAt").asText()),
                    shortOrNull(node, "distanceMm"),
                    shortOrNull(node, "tempTenthsCelsius"),
                    shortOrNull(node, "rssiDbm"),
                    doubleOrNull(node, "levelPct"),
                    node.has("quality") ? node.get("quality").asText() : "GOOD",
                    node.has("wireVersion") ? node.get("wireVersion").asInt() : 1);

        } catch (Exception e) {
            log.error("failed to persist record at offset {} partition {}: {}",
                    record.offset(), record.partition(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static Short shortOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? (short) node.get(field).asInt() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asDouble() : null;
    }
}
