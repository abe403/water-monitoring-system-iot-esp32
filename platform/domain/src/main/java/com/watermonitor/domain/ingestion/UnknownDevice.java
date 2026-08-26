package com.watermonitor.domain.ingestion;

import com.watermonitor.domain.device.DeviceId;

public class UnknownDevice extends RuntimeException {

    public UnknownDevice(DeviceId id) {
        super("no registered device: " + id);
    }
}
