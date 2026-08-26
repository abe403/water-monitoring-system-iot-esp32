package com.watermonitor.domain.ingestion;

import com.watermonitor.domain.device.DeviceId;

import java.time.Instant;

/**
 * The bytes exactly as received on {@code wtm/v1/<id>/tel}, before decoding.
 * Kept alongside {@link WireFormat} so a decode failure can dead-letter the
 * original payload for later replay against a fixed or updated decoder.
 */
public record RawBatch(DeviceId deviceId, WireFormat wireFormat, byte[] payload, Instant receivedAt) {
}
