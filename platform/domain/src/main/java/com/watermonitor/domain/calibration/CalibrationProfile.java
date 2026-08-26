package com.watermonitor.domain.calibration;

import com.watermonitor.domain.device.DeviceId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A versioned, time-bounded calibration for one device. Immutable —
 * recalibrating a device creates a new profile version rather than mutating
 * this one, specifically so that raw history can be replayed through
 * whichever profile was actually in effect when each reading was taken. See
 * {@link com.watermonitor.domain.ports.CalibrationProfileRepository#effectiveAt}.
 */
public record CalibrationProfile(
        ProfileId id,
        DeviceId deviceId,
        int version,
        CalibrationStrategy strategy,
        Instant validFrom,
        Optional<Instant> validTo) {

    public CalibrationProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validTo, "validTo");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1: " + version);
        }
        if (validTo.isPresent() && !validTo.get().isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo must be after validFrom");
        }
    }

    public boolean coversInstant(Instant when) {
        boolean afterStart = !when.isBefore(validFrom);
        boolean beforeEnd = validTo.map(when::isBefore).orElse(true);
        return afterStart && beforeEnd;
    }

    public record ProfileId(String value) {
    }
}
