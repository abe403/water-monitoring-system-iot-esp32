package com.watermonitor.domain.alert;

/** The physically-defined fault categories the detection pipeline distinguishes between. */
public enum AnomalyType {
    LEAK,
    SENSOR_FAULT,
    PUMP_FAILURE,
    THERMAL,
    COMMS_GAP,
}
