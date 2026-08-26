package com.watermonitor.domain.telemetry;

/** A raw ultrasonic distance reading, in metres. Uncalibrated. */
public record Distance(double metres) implements Quantity {

    public static Distance ofMillimetres(int mm) {
        return new Distance(mm / 1000.0);
    }

    @Override
    public double canonicalValue() {
        return metres;
    }
}
