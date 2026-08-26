package com.watermonitor.ingest.adapter;

import com.watermonitor.domain.device.BootId;
import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.device.Sequence;
import com.watermonitor.domain.ports.AckPort;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public final class MqttAckAdapter implements AckPort {

    private static final int QOS = 1;
    private final MqttClient mqttClient;

    public MqttAckAdapter(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @Override
    public void acknowledgeThrough(DeviceId deviceId, BootId bootId, Sequence highestContiguous) {
        String topic = "wtm/v1/" + deviceId.value() + "/ack";
        byte[] payload = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) highestContiguous.value())
                .array();

        MqttMessage msg = new MqttMessage(payload);
        msg.setQos(QOS);
        msg.setRetained(false);

        try {
            mqttClient.publish(topic, msg);
        } catch (MqttException e) {
            throw new IllegalStateException("failed to publish ack to " + topic, e);
        }
    }
}
