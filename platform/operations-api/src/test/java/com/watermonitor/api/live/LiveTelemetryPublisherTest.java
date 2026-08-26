package com.watermonitor.api.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LiveTelemetryPublisherTest {

    @Test
    void publishesToFleetAndDeviceTopics() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        LiveTelemetryPublisher publisher = new LiveTelemetryPublisher(messaging, new ObjectMapper());
        byte[] payload = """
                {"deviceId":"tank-01","bootId":7,"seq":42,"levelPct":50.0}
                """.getBytes(StandardCharsets.UTF_8);

        publisher.consume(new ConsumerRecord<>("telemetry.enriched.v1", 1, 20, "tank-01", payload));

        ArgumentCaptor<Object> devicePayload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/telemetry/tank-01"), devicePayload.capture());
        verify(messaging).convertAndSend(eq("/topic/telemetry"), any(Object.class));
        assertThat(((JsonNode) devicePayload.getValue()).get("seq").asLong()).isEqualTo(42);
    }
}
