package com.watermonitor.alert.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watermonitor.domain.alert.Alert;
import com.watermonitor.domain.alert.AlertState;
import com.watermonitor.domain.alert.AnomalyType;
import com.watermonitor.domain.device.DeviceId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Consumes anomaly scores from the inference service and manages the alert
 * lifecycle with hysteresis — a score above threshold opens or re-opens an
 * alert; sustained normal scores resolve it. This prevents flapping on
 * borderline scores.
 */
@Component
public final class AnomalyScoreConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnomalyScoreConsumer.class);
    private static final double DEFAULT_THRESHOLD = 0.7;
    private static final Duration HYSTERESIS_WINDOW = Duration.ofMinutes(15);

    private final AlertJpaRepository alertRepo;
    private final ObjectMapper json;

    public AnomalyScoreConsumer(AlertJpaRepository alertRepo, ObjectMapper json) {
        this.alertRepo = alertRepo;
        this.json = json;
    }

    @KafkaListener(
            topics = "anomaly.scores.v1",
            groupId = "alert-service",
            properties = {
                    "auto.offset.reset=earliest",
                    "enable.auto.commit=false"
            })
    public void consume(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode node = json.readTree(record.value());
            String deviceId = node.get("deviceId").asText();
            double score = node.get("score").asDouble();
            String anomalyType = node.has("anomalyType")
                    ? node.get("anomalyType").asText()
                    : "UNKNOWN";
            Instant scoredAt = Instant.parse(node.get("scoredAt").asText());

            if (score >= DEFAULT_THRESHOLD) {
                handleFiring(deviceId, anomalyType, score, scoredAt);
            } else {
                handleNormal(deviceId, scoredAt);
            }

        } catch (Exception e) {
            log.error("failed to process anomaly score at offset {}: {}",
                    record.offset(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void handleFiring(String deviceId, String anomalyType, double score, Instant at) {
        AlertEntity existing = alertRepo.findActiveByDeviceId(deviceId);
        if (existing != null) {
            existing.setScore(score);
            existing.setUpdatedAt(at);
            alertRepo.save(existing);
            log.debug("updated existing alert {} for device {}, score={}", existing.getId(), deviceId, score);
        } else {
            AlertEntity alert = new AlertEntity();
            alert.setDeviceId(deviceId);
            alert.setAnomalyType(anomalyType);
            alert.setState(AlertState.OPEN.name());
            alert.setScore(score);
            alert.setOpenedAt(at);
            alert.setUpdatedAt(at);
            alertRepo.save(alert);
            log.info("opened alert for device {} type={} score={}", deviceId, anomalyType, score);
        }
    }

    private void handleNormal(String deviceId, Instant at) {
        AlertEntity existing = alertRepo.findActiveByDeviceId(deviceId);
        if (existing == null) return;

        Instant lastUpdate = existing.getUpdatedAt();
        if (lastUpdate != null && Duration.between(lastUpdate, at).compareTo(HYSTERESIS_WINDOW) >= 0) {
            existing.setState(AlertState.RESOLVED.name());
            existing.setResolvedAt(at);
            existing.setResolvedBy("auto-hysteresis");
            existing.setUpdatedAt(at);
            alertRepo.save(existing);
            log.info("auto-resolved alert {} for device {} after {} sustained normal",
                    existing.getId(), deviceId, HYSTERESIS_WINDOW);
        }
    }
}
