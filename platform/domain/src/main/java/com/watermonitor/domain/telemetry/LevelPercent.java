package com.watermonitor.domain.telemetry;

/**
 * A calibrated tank fill level, 0-100%.
 *
 * <p>The constructor's range check exists specifically because of a bug found
 * in the project this rewrite replaces: the original firmware's calibration
 * lambda ({@code level = -192.31 * distance + 138.46}) had a valid input
 * range that disagreed with the sensor's own clamp filter, so a legitimate
 * clamped distance reading could produce a level below -80%. Making an
 * out-of-range {@code LevelPercent} impossible to construct turns that class
 * of bug into a compile-time-adjacent guarantee: nothing downstream of a
 * {@link CalibrationStrategy} ever needs to re-check the bound, because it
 * cannot hold an invalid value in the first place. See
 * docs/DECISIONS/0002-server-side-calibration.md.
 */
public record LevelPercent(double value) implements Quantity {

    public LevelPercent {
        if (value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(
                    "level percent out of range [0,100]: %s — a CalibrationStrategy must clamp before constructing this"
                            .formatted(value));
        }
    }

    @Override
    public double canonicalValue() {
        return value;
    }
}
