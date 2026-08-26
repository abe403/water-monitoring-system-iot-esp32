package com.watermonitor.domain.ports;

import com.watermonitor.domain.calibration.CalibrationProfile;
import com.watermonitor.domain.device.DeviceId;

import java.time.Instant;
import java.util.Optional;

public interface CalibrationProfileRepository {

    /**
     * The profile in effect at {@code observedAt} — deliberately not "the
     * current profile". Enrichment must apply the calibration that was
     * actually active when a reading was taken, so replaying old raw
     * telemetry after a recalibration reproduces the historically correct
     * level rather than silently reinterpreting the past.
     */
    Optional<CalibrationProfile> effectiveAt(DeviceId device, Instant observedAt);
}
