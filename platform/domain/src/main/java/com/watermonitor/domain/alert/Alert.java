package com.watermonitor.domain.alert;

import com.watermonitor.domain.device.DeviceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An alert's lifecycle. The state machine is the aggregate's whole job:
 * every transition is validated against {@link #ALLOWED} and every
 * transition is recorded, so "what happened to this alert and when" is
 * always answerable without reaching into infrastructure.
 */
public final class Alert {

    private static final Map<AlertState, Set<AlertState>> ALLOWED = new EnumMap<>(Map.of(
            AlertState.OPEN, EnumSet.of(AlertState.ACKNOWLEDGED, AlertState.SUPPRESSED, AlertState.EXPIRED),
            AlertState.ACKNOWLEDGED, EnumSet.of(AlertState.RESOLVED, AlertState.EXPIRED),
            AlertState.SUPPRESSED, EnumSet.of(AlertState.OPEN, AlertState.EXPIRED),
            AlertState.RESOLVED, EnumSet.noneOf(AlertState.class),
            AlertState.EXPIRED, EnumSet.noneOf(AlertState.class)));

    private final AlertId id;
    private final DeviceId deviceId;
    private final AnomalyType type;
    private final String dedupeKey;
    private final List<Transition> history = new ArrayList<>();
    private AlertState state;

    public Alert(AlertId id, DeviceId deviceId, AnomalyType type, String dedupeKey, Instant openedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.type = Objects.requireNonNull(type, "type");
        this.dedupeKey = Objects.requireNonNull(dedupeKey, "dedupeKey");
        this.state = AlertState.OPEN;
        this.history.add(new Transition(AlertState.OPEN, Operator.SYSTEM, openedAt));
    }

    public void acknowledge(Operator by, Instant at) {
        transitionTo(AlertState.ACKNOWLEDGED, by, at);
    }

    public void resolve(Operator by, Instant at) {
        transitionTo(AlertState.RESOLVED, by, at);
    }

    public void suppress(Operator by, Instant at) {
        transitionTo(AlertState.SUPPRESSED, by, at);
    }

    public void reopen(Operator by, Instant at) {
        transitionTo(AlertState.OPEN, by, at);
    }

    public void expire(Instant at) {
        transitionTo(AlertState.EXPIRED, Operator.SYSTEM, at);
    }

    private void transitionTo(AlertState next, Operator by, Instant at) {
        if (!ALLOWED.get(state).contains(next)) {
            throw new IllegalTransition(state, next);
        }
        this.state = next;
        this.history.add(new Transition(next, by, at));
    }

    public AlertId id() {
        return id;
    }

    public DeviceId deviceId() {
        return deviceId;
    }

    public AnomalyType type() {
        return type;
    }

    public String dedupeKey() {
        return dedupeKey;
    }

    public AlertState state() {
        return state;
    }

    public List<Transition> history() {
        return List.copyOf(history);
    }

    public boolean isOpenOrAcknowledged() {
        return state == AlertState.OPEN || state == AlertState.ACKNOWLEDGED;
    }

    public record Transition(AlertState to, Operator by, Instant at) {
    }

    public record AlertId(String value) {
    }
}
