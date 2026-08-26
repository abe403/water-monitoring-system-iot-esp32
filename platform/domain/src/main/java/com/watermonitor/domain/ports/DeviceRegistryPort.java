package com.watermonitor.domain.ports;

import com.watermonitor.domain.device.Device;
import com.watermonitor.domain.device.DeviceId;

import java.util.Optional;

public interface DeviceRegistryPort {

    Optional<Device> findById(DeviceId id);
}
