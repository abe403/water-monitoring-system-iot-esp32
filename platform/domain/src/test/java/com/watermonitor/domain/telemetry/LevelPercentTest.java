package com.watermonitor.domain.telemetry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LevelPercentTest {

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, -83.0, 100.01, 1000.0})
    void outOfRangeValues_areRejected(double value) {
        assertThatThrownBy(() -> new LevelPercent(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 50.0, 99.99, 100.0})
    void inRangeValues_areAccepted(double value) {
        new LevelPercent(value); // does not throw
    }
}
