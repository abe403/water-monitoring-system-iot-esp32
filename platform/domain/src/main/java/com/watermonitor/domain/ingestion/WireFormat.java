package com.watermonitor.domain.ingestion;

/**
 * Identifies the byte layout of a {@link RawBatch}'s payload. New firmware
 * wire versions are added here and picked up by a new
 * {@link FrameDecoder#supports(WireFormat)} implementation — old devices
 * keep working against old decoders indefinitely, since firmware fleets
 * never upgrade atomically.
 */
public record WireFormat(int version) {

    public static final WireFormat TELEMETRY_OUTBOX_V1 = new WireFormat(1);
}
