package com.watermonitor.domain.alert;

public class IllegalTransition extends RuntimeException {

    public IllegalTransition(AlertState from, AlertState to) {
        super("cannot transition alert from %s to %s".formatted(from, to));
    }
}
