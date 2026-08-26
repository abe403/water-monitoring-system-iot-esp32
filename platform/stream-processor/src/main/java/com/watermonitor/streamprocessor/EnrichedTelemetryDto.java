package com.watermonitor.streamprocessor;

import java.time.Instant;

public record EnrichedTelemetryDto(
        String deviceId,
        long bootId,
        long seq,
        Instant observedAt,
        Instant receivedAt,
        Short distanceMm,
        Short tempTenthsCelsius,
        Short rssiDbm,
        Double levelPct,
        String quality,
        int wireVersion,
        long interarrivalMs) {
}
