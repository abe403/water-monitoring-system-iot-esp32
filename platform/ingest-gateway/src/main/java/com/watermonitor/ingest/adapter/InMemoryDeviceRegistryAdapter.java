package com.watermonitor.ingest.adapter;

import com.watermonitor.domain.device.Device;
import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.ports.DeviceRegistryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Auto-provisions any device on first contact. A production implementation
 * would be backed by the compacted {@code device.registry.v1} topic; this
 * auto-provision strategy is deliberate for the MVP so that new hardware can
 * start publishing without a registration ceremony.
 */
@Component
public final class InMemoryDeviceRegistryAdapter implements DeviceRegistryPort {

    @Override
    public Optional<Device> findById(DeviceId id) {
        return Optional.of(new Device(id, "unknown", "unknown", true));
    }
}
