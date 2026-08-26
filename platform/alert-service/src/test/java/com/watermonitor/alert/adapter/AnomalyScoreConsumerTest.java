package com.watermonitor.alert.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnomalyScoreConsumerTest {

    private final AlertJpaRepository repository = mock(AlertJpaRepository.class);
    private final AnomalyScoreConsumer consumer = new AnomalyScoreConsumer(repository, new ObjectMapper());

    @Test
    void firingScoreOpensAlert() {
        when(repository.findActiveByDeviceId("tank-01")).thenReturn(null);

        consume(0.91, "LEAK", "2024-08-23T08:00:00Z");

        ArgumentCaptor<AlertEntity> saved = ArgumentCaptor.forClass(AlertEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDeviceId()).isEqualTo("tank-01");
        assertThat(saved.getValue().getAnomalyType()).isEqualTo("LEAK");
        assertThat(saved.getValue().getState()).isEqualTo("OPEN");
        assertThat(saved.getValue().getScore()).isEqualTo(0.91);
    }

    @Test
    void firingScoreUpdatesActiveAlertWithoutOpeningAnother() {
        AlertEntity active = activeAt("2024-08-23T08:00:00Z");
        when(repository.findActiveByDeviceId("tank-01")).thenReturn(active);

        consume(0.82, "LEAK", "2024-08-23T08:05:00Z");

        verify(repository).save(active);
        assertThat(active.getScore()).isEqualTo(0.82);
        assertThat(active.getUpdatedAt()).isEqualTo(Instant.parse("2024-08-23T08:05:00Z"));
    }

    @Test
    void normalScoreAtHysteresisBoundaryResolvesAlert() {
        AlertEntity active = activeAt("2024-08-23T08:00:00Z");
        when(repository.findActiveByDeviceId("tank-01")).thenReturn(active);

        consume(0.10, "LEAK", "2024-08-23T08:15:00Z");

        verify(repository).save(active);
        assertThat(active.getState()).isEqualTo("RESOLVED");
        assertThat(active.getResolvedBy()).isEqualTo("auto-hysteresis");
        assertThat(active.getResolvedAt()).isEqualTo(Instant.parse("2024-08-23T08:15:00Z"));
    }

    @Test
    void normalScoreBeforeHysteresisBoundaryKeepsAlertActive() {
        AlertEntity active = activeAt("2024-08-23T08:00:00Z");
        when(repository.findActiveByDeviceId("tank-01")).thenReturn(active);

        consume(0.10, "LEAK", "2024-08-23T08:14:59Z");

        verify(repository, never()).save(any());
        assertThat(active.getState()).isEqualTo("OPEN");
    }

    private void consume(double score, String type, String scoredAt) {
        byte[] payload = """
                {"deviceId":"tank-01","score":%s,"anomalyType":"%s","scoredAt":"%s"}
                """.formatted(score, type, scoredAt).getBytes(StandardCharsets.UTF_8);
        consumer.consume(new ConsumerRecord<>("anomaly.scores.v1", 0, 1, "tank-01", payload));
    }

    private static AlertEntity activeAt(String updatedAt) {
        AlertEntity entity = new AlertEntity();
        entity.setDeviceId("tank-01");
        entity.setAnomalyType("LEAK");
        entity.setState("OPEN");
        entity.setScore(0.75);
        entity.setOpenedAt(Instant.parse(updatedAt));
        entity.setUpdatedAt(Instant.parse(updatedAt));
        return entity;
    }
}
