package com.watermonitor.domain.telemetry;

/**
 * Bookkeeping about how a {@link TelemetryRecord} arrived, kept separate from
 * the physical readings themselves so a decoder change never touches
 * measurement data.
 */
public record TransportMetadata(int wireFormatVersion, int checksumErrorsAtReceipt) {
}
