package com.watermonitor.domain.ports;

import com.watermonitor.domain.device.BootId;
import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.device.Sequence;

/**
 * The application-level acknowledgement back to the device, published on
 * {@code wtm/v1/<id>/ack} only after {@link TelemetryPublisherPort#publish}
 * has completed. This is deliberately stronger than an MQTT PUBACK, which
 * only proves the broker has the message, not that Kafka does.
 */
public interface AckPort {

    void acknowledgeThrough(DeviceId deviceId, BootId bootId, Sequence highestContiguous);
}
