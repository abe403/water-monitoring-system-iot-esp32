package com.watermonitor.domain.telemetry;

/**
 * A physically typed measurement. The sealed hierarchy exists to prevent
 * unit-confusion bugs — a {@link Distance} can never be passed where a
 * {@link Temperature} is expected, the way two bare {@code double}s could be
 * silently swapped.
 */
public sealed interface Quantity
        permits Distance, Temperature, SignalStrength, LevelPercent {

    /** The value in this quantity's SI (or otherwise canonical) unit. */
    double canonicalValue();
}
