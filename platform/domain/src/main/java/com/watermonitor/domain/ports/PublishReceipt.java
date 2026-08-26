package com.watermonitor.domain.ports;

import com.watermonitor.domain.device.IdempotencyKey;

/** Proof that a {@code TelemetryPublisherPort.publish} call is durably committed. */
public record PublishReceipt(IdempotencyKey key, long kafkaOffset, int kafkaPartition) {
}
