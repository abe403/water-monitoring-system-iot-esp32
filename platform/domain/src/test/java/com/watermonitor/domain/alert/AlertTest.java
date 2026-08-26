package com.watermonitor.domain.alert;

import com.watermonitor.domain.device.DeviceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertTest {

    private final Instant now = Instant.parse("2026-08-24T00:00:00Z");

    private Alert newAlert() {
        return new Alert(new Alert.AlertId("a-1"), new DeviceId("water-tank-01"),
                AnomalyType.LEAK, "leak:water-tank-01:2026-08-24", now);
    }

    @Test
    void opensInOpenState() {
        assertThat(newAlert().state()).isEqualTo(AlertState.OPEN);
    }

    @Test
    void openToAcknowledgedToResolved_isAllowed() {
        Alert alert = newAlert();
        alert.acknowledge(new Operator("op-1", "Alex"), now);
        alert.resolve(new Operator("op-1", "Alex"), now);

        assertThat(alert.state()).isEqualTo(AlertState.RESOLVED);
        assertThat(alert.history()).hasSize(3); // OPEN, ACKNOWLEDGED, RESOLVED
    }

    @Test
    void suppressedCanReopen() {
        Alert alert = newAlert();
        alert.suppress(Operator.SYSTEM, now);
        alert.reopen(new Operator("op-1", "Alex"), now);

        assertThat(alert.state()).isEqualTo(AlertState.OPEN);
    }

    @Test
    void resolvedIsTerminal_cannotReopen() {
        Alert alert = newAlert();
        alert.acknowledge(Operator.SYSTEM, now);
        alert.resolve(Operator.SYSTEM, now);

        assertThatThrownBy(() -> alert.reopen(Operator.SYSTEM, now))
                .isInstanceOf(IllegalTransition.class);
    }

    @Test
    void cannotResolveWithoutAcknowledging() {
        Alert alert = newAlert();

        assertThatThrownBy(() -> alert.resolve(Operator.SYSTEM, now))
                .isInstanceOf(IllegalTransition.class);
    }

    @Test
    void resolvedAndExpiredAreTerminal_noTransitionEscapesThem() {
        Alert resolved = newAlert();
        resolved.acknowledge(Operator.SYSTEM, now);
        resolved.resolve(Operator.SYSTEM, now);
        assertThatThrownBy(() -> resolved.acknowledge(Operator.SYSTEM, now)).isInstanceOf(IllegalTransition.class);
        assertThatThrownBy(() -> resolved.suppress(Operator.SYSTEM, now)).isInstanceOf(IllegalTransition.class);
        assertThatThrownBy(() -> resolved.reopen(Operator.SYSTEM, now)).isInstanceOf(IllegalTransition.class);

        Alert expired = newAlert();
        expired.expire(now);
        assertThatThrownBy(() -> expired.acknowledge(Operator.SYSTEM, now)).isInstanceOf(IllegalTransition.class);
        assertThatThrownBy(() -> expired.reopen(Operator.SYSTEM, now)).isInstanceOf(IllegalTransition.class);
    }
}
