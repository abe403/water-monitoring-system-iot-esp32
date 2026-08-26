package com.watermonitor.sink.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TelemetryKafkaConsumerTest {

    @Test
    void mapsEnrichedEventToIdempotentRepositoryKey() {
        ReadingJdbcRepository repository = mock(ReadingJdbcRepository.class);
        TelemetryKafkaConsumer consumer = new TelemetryKafkaConsumer(repository, new ObjectMapper());
        byte[] payload = """
                {"deviceId":"tank-01","bootId":7,"seq":42,
                 "observedAt":"2024-08-23T08:00:00Z",
                 "receivedAt":"2024-08-23T08:00:00.050Z",
                 "distanceMm":460,"tempTenthsCelsius":245,"rssiDbm":-62,
                 "levelPct":50.0,"quality":"GOOD","wireVersion":1}
                """.getBytes(StandardCharsets.UTF_8);

        consumer.consume(new ConsumerRecord<>("telemetry.enriched.v1", 2, 99, "tank-01", payload));

        verify(repository).upsert(
                "tank-01", 7, 42,
                Instant.parse("2024-08-23T08:00:00Z"),
                Instant.parse("2024-08-23T08:00:00.050Z"),
                (short) 460, (short) 245, (short) -62,
                50.0, "GOOD", 1);
    }
}
