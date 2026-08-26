package com.watermonitor.domain.telemetry;

/** How much a {@link Reading} should be trusted. Assigned by enrichment, never by the device. */
public enum Quality {
    GOOD,
    SUSPECT_OUT_OF_RANGE,
    BAD_CHECKSUM,
    STALE,
    INTERPOLATED,
}
