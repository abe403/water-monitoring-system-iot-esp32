package com.watermonitor.domain.alert;

/**
 * An alert's lifecycle state. Deliberately a plain enum rather than the GoF
 * State pattern (a class per state): there is no behavioural variation
 * between states, only a transition table (see {@link Alert}), and five
 * classes to express that table would need awkward persistence/serialization
 * support for zero benefit. See the "where OOD is the wrong tool" discussion
 * in docs/ARCHITECTURE.md.
 */
public enum AlertState {
    OPEN,
    ACKNOWLEDGED,
    SUPPRESSED,
    RESOLVED,
    EXPIRED,
}
