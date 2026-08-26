package com.watermonitor.domain.telemetry;

import java.time.Instant;
import java.util.Objects;

/** One channel's measurement, enriched with a quality assessment. */
public record Reading(Quantity value, Quality quality, Instant observedAt) {

    public Reading {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(observedAt, "observedAt");
    }

    public boolean isTrustworthy() {
        return quality == Quality.GOOD || quality == Quality.INTERPOLATED;
    }
}
