package com.watermonitor.domain.calibration;

/**
 * Physical dimensions of the tank a distance reading is measuring into.
 * {@code sensorOffsetMetres} is the gap between the sensor face and the
 * "full" waterline (i.e. the minimum distance the sensor can ever report).
 */
public record TankGeometry(double heightMetres, double sensorOffsetMetres) {

    public TankGeometry {
        if (heightMetres <= 0) {
            throw new IllegalArgumentException("heightMetres must be positive: " + heightMetres);
        }
        if (sensorOffsetMetres < 0) {
            throw new IllegalArgumentException("sensorOffsetMetres cannot be negative: " + sensorOffsetMetres);
        }
    }
}
