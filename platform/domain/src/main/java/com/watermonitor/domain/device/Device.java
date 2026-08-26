package com.watermonitor.domain.device;

/** A registered physical node. Minimal by design — grow this only when a real consumer needs a field. */
public record Device(DeviceId id, String hardwareRevision, String firmwareVersion, boolean provisioned) {
}
