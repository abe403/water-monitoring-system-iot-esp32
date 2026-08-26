package com.watermonitor.domain.device;

/**
 * The composite key that makes at-least-once delivery safe to treat as
 * effectively-once at the persistence boundary. {@code timescale-sink}
 * upserts on exactly this tuple ({@code ON CONFLICT (device_id, boot_id, seq)
 * DO NOTHING}), so redelivering the same record after a crash-before-commit
 * is a no-op rather than a duplicate row.
 */
public record IdempotencyKey(DeviceId deviceId, BootId bootId, Sequence seq) {
}
