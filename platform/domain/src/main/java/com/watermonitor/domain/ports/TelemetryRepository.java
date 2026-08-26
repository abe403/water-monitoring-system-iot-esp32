package com.watermonitor.domain.ports;

import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.telemetry.TelemetryRecord;

import java.time.Instant;
import java.util.List;

/** Persistence port. {@code upsert} must be idempotent on {@code record.idempotencyKey()}. */
public interface TelemetryRepository {

    void upsert(TelemetryRecord record);

    List<TelemetryRecord> range(DeviceId id, Instant from, Instant to);
}
