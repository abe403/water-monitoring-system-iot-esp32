package com.watermonitor.domain.telemetry;

/** WiFi RSSI, in dBm. Typically in the range [-100, 0]; not enforced here. */
public record SignalStrength(double dBm) implements Quantity {

    @Override
    public double canonicalValue() {
        return dBm;
    }

    /** Matches the firmware's own RSSI-to-percent mapping (see water-level.yaml). */
    public double asPercent() {
        return Math.min(Math.max(2.0 * (dBm + 100.0), 0.0), 100.0);
    }
}
