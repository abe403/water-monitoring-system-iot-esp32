package com.watermonitor.domain.device;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A device's stable identifier, e.g. {@code "water-tank-01"}. Matches the
 * first path segment of the MQTT telemetry topic {@code wtm/v1/<id>/tel}.
 */
public record DeviceId(String value) {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9-]{2,63}$");

    public DeviceId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "invalid device id '%s': expected 3-64 lowercase alphanumeric/hyphen characters"
                            .formatted(value));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
