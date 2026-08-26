package com.watermonitor.domain.telemetry;

/** A battery NTC thermistor reading, in degrees Celsius. */
public record Temperature(double celsius) implements Quantity {

    public static Temperature ofTenthsCelsius(int tenths) {
        return new Temperature(tenths / 10.0);
    }

    @Override
    public double canonicalValue() {
        return celsius;
    }
}
