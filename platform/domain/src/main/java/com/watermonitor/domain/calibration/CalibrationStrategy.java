package com.watermonitor.domain.calibration;

import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.LevelPercent;

/**
 * Converts a raw distance measurement into a calibrated fill level.
 *
 * <p>Strategy: this is genuinely pluggable, not decoration — different tanks
 * have different geometries and different installers produce different
 * two-point calibrations, and a project's calibration approach is expected
 * to evolve (linear now, piecewise or geometric later) independent of
 * everything else in the enrichment pipeline.
 *
 * <p>Every implementation must return a {@link LevelPercent}, whose
 * constructor enforces {@code [0, 100]} — so "clamp before you construct" is
 * not a convention implementations have to remember, it is enforced by the
 * type they are required to return.
 */
public interface CalibrationStrategy {

    LevelPercent toLevel(Distance measured);

    CalibrationKind kind();

    enum CalibrationKind {
        LINEAR_TWO_POINT,
        PIECEWISE_LINEAR,
        GEOMETRIC_VOLUME,
    }
}
