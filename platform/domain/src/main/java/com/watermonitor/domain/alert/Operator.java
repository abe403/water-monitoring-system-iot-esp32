package com.watermonitor.domain.alert;

/** The human (or "system", for automated transitions) who caused an alert state change. */
public record Operator(String id, String displayName) {

    public static final Operator SYSTEM = new Operator("system", "Automated");
}
