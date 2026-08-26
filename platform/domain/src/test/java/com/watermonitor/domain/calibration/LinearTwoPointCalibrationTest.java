package com.watermonitor.domain.calibration;

import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.LevelPercent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The scenario in {@link #distanceBeyondEmptyPoint_clampsToZero_insteadOfGoingNegative()}
 * is the exact bug found in the original project: the firmware's clamp
 * allowed distances up to 1.15m but the calibration line hit 0% at 0.72m, so
 * a legitimately clamped reading could compute a level around -83%. This
 * test is the regression guard for docs/DECISIONS/0002-server-side-calibration.md.
 */
class LinearTwoPointCalibrationTest {

    // Matches the values implied by the original firmware's calibration
    // lambda (`level = -192.31 * distance + 138.46`): 100% at 0.20m, 0% at 0.72m.
    private final LinearTwoPointCalibration calibration =
            new LinearTwoPointCalibration(new Distance(0.20), new Distance(0.72));

    @Test
    void distanceBeyondEmptyPoint_clampsToZero_insteadOfGoingNegative() {
        LevelPercent level = calibration.toLevel(new Distance(1.15));

        assertThat(level.value()).isEqualTo(0.0);
    }

    @Test
    void distanceAtFullPoint_isOneHundredPercent() {
        assertThat(calibration.toLevel(new Distance(0.20)).value()).isCloseTo(100.0, within(0.01));
    }

    @Test
    void distanceAtEmptyPoint_isZeroPercent() {
        assertThat(calibration.toLevel(new Distance(0.72)).value()).isCloseTo(0.0, within(0.01));
    }

    @ParameterizedTest
    @CsvSource({
            "0.10, 100.0",  // closer than "full" — still clamps, does not exceed 100
            "0.46, 50.0",   // midpoint
            "0.72, 0.0",
            "2.00, 0.0",    // sensor read a wall/ceiling far past the tank — still valid
    })
    void everyOutput_isAlwaysWithinValidRange(double distanceMetres, double expectedPercent) {
        LevelPercent level = calibration.toLevel(new Distance(distanceMetres));

        assertThat(level.value()).isCloseTo(expectedPercent, within(0.5));
    }

    @Test
    void everyPossibleOutput_isConstructible_becauseTheTypeForbidsOutOfRange() {
        // There is no assertion beyond "this doesn't throw" — that's the point.
        // LevelPercent's own constructor makes an out-of-range result
        // impossible to represent, so this strategy cannot violate the
        // invariant even if its arithmetic were wrong.
        for (double d = -5.0; d <= 5.0; d += 0.01) {
            calibration.toLevel(new Distance(d));
        }
    }

    @Test
    void identicalCalibrationPoints_areRejectedAtConstruction() {
        assertThatThrownBy(() -> new LinearTwoPointCalibration(new Distance(0.5), new Distance(0.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
