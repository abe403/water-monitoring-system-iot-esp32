package com.watermonitor.domain.telemetry;

import com.watermonitor.domain.device.BootId;
import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.device.IdempotencyKey;
import com.watermonitor.domain.device.Sequence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One decoded, enriched telemetry event: a device's readings at a point in
 * time, keyed for idempotent persistence. This is the shape that flows
 * through {@code telemetry.enriched.v1} — the raw wire frame is decoded into
 * this by a {@link com.watermonitor.domain.ingestion.FrameDecoder} before it
 * ever reaches the rest of the domain.
 */
public record TelemetryRecord(
        DeviceId deviceId,
        BootId bootId,
        Sequence seq,
        Instant observedAt,
        Instant ingestedAt,
        List<Reading> readings,
        TransportMetadata transport) {

    public TelemetryRecord {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(bootId, "bootId");
        Objects.requireNonNull(seq, "seq");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        readings = List.copyOf(readings);
    }

    public IdempotencyKey idempotencyKey() {
        return new IdempotencyKey(deviceId, bootId, seq);
    }

    /** Convenience lookup used by calibration and alerting; O(n) over a small list. */
    public <Q extends Quantity> Reading readingOf(Class<Q> type) {
        return readings.stream()
                .filter(r -> type.isInstance(r.value()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no %s reading on record %s".formatted(type.getSimpleName(), idempotencyKey())));
    }
}
