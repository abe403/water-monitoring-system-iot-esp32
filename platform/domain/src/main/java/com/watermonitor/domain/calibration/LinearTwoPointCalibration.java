package com.watermonitor.domain.calibration;

import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.LevelPercent;

/**
 * Two-point linear interpolation between a measured "full" distance and a
 * measured "empty" distance. Replaces the original firmware's hardcoded
 * lambda ({@code level = -192.31 * x + 138.46}), whose valid input range
 * silently disagreed with the sensor's own clamp filter — see
 * docs/DECISIONS/0002-server-side-calibration.md. Here, out-of-range inputs
 * are clamped explicitly and the result is always a valid
 * {@link LevelPercent} by construction.
 */
public record LinearTwoPointCalibration(Distance fullAt, Distance emptyAt) implements CalibrationStrategy {

    public LinearTwoPointCalibration {
        if (fullAt.metres() == emptyAt.metres()) {
            throw new IllegalArgumentException("fullAt and emptyAt must differ: both %sm".formatted(fullAt.metres()));
        }
    }

    @Override
    public LevelPercent toLevel(Distance measured) {
        double fraction = (emptyAt.metres() - measured.metres()) / (emptyAt.metres() - fullAt.metres());
        double clampedPercent = Math.min(Math.max(fraction * 100.0, 0.0), 100.0);
        return new LevelPercent(clampedPercent);
    }

    @Override
    public CalibrationKind kind() {
        return CalibrationKind.LINEAR_TWO_POINT;
    }
}
