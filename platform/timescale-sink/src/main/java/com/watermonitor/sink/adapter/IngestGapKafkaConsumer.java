package com.watermonitor.sink.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public final class IngestGapKafkaConsumer {

    private final IngestGapJdbcRepository repository;
    private final ObjectMapper json;

    public IngestGapKafkaConsumer(IngestGapJdbcRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @KafkaListener(
            topics = "ingest.gap.v1",
            groupId = "timescale-sink-gaps",
            properties = {
                    "auto.offset.reset=earliest",
                    "enable.auto.commit=false"
            })
    public void consume(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode node = json.readTree(record.value());
            repository.insert(
                    node.get("deviceId").asText(),
                    node.get("bootId").asLong(),
                    node.get("missingSeqFrom").asLong(),
                    node.get("missingSeqTo").asLong(),
                    node.get("gapSize").asLong());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to persist gap at partition " + record.partition() +
                            " offset " + record.offset(), e);
        }
    }
}
