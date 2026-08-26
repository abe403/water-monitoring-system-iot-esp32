package com.watermonitor.ingest.adapter;

import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.ingestion.IngestOutcome;
import com.watermonitor.domain.ingestion.IngestTelemetryBatchUseCase;
import com.watermonitor.domain.ingestion.RawBatch;
import com.watermonitor.domain.ingestion.UnknownDevice;
import com.watermonitor.domain.ingestion.WireFormat;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

/**
 * Subscribes to {@code wtm/v1/+/tel} and hands each message to the
 * domain use case. Uses QoS 1 with manual acknowledgement: the MQTT
 * PUBACK is sent only after the use case returns, which means only
 * after Kafka has durably replicated the record. A transient failure
 * (broker down, Kafka timeout) lets the exception propagate, the PUBACK
 * is never sent, and the broker redelivers — which is exactly the
 * backpressure behaviour the durability chain requires.
 */
@Component
public final class MqttTelemetryInboundAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryInboundAdapter.class);
    private static final String TOPIC_PATTERN = "wtm/v1/+/tel";

    private final MqttClient mqttClient;
    private final MqttConnectOptions connectOptions;
    private final IngestTelemetryBatchUseCase useCase;
    private final int qos;
    private final ExecutorService ingestWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mqtt-ingest-worker");
        thread.setDaemon(true);
        return thread;
    });

    public MqttTelemetryInboundAdapter(
            MqttClient mqttClient,
            MqttConnectOptions connectOptions,
            IngestTelemetryBatchUseCase useCase,
            @Value("${wtm.mqtt.subscribe-qos:1}") int qos) {
        this.mqttClient = mqttClient;
        this.connectOptions = connectOptions;
        this.useCase = useCase;
        this.qos = qos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() throws MqttException {
        // Paho normally sends the inbound PUBACK when messageArrived returns.
        // Work is dispatched off the callback thread so the synchronous
        // application-ACK publish cannot deadlock its own delivery callback;
        // manual completion retains the Kafka-before-PUBACK guarantee.
        mqttClient.setManualAcks(true);
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    try {
                        subscribeToTelemetry();
                    } catch (MqttException e) {
                        log.error("failed to restore MQTT subscription", e);
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost; automatic reconnect is enabled", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                ingestWorker.execute(() -> processAndAcknowledge(topic, message));
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                // Outbound application acknowledgements need no extra action.
            }
        });
        mqttClient.connect(connectOptions);
        subscribeToTelemetry();
    }

    private void subscribeToTelemetry() throws MqttException {
        log.info("subscribing to {} at QoS {}", TOPIC_PATTERN, qos);
        mqttClient.subscribe(TOPIC_PATTERN, qos);
    }

    private void handleMessage(String topic, MqttMessage message) {
        DeviceId deviceId = extractDeviceId(topic);
        Instant receivedAt = Instant.now();

        RawBatch batch = new RawBatch(
                deviceId,
                WireFormat.TELEMETRY_OUTBOX_V1,
                message.getPayload(),
                receivedAt);

        try {
            IngestOutcome outcome = useCase.handle(batch);
            log.debug("device={} outcome={} count={}", deviceId.value(), outcome.result(), outcome.recordCount());
        } catch (UnknownDevice e) {
            log.warn("unknown device: {}", deviceId.value());
            throw e;
        }
    }

    private void processAndAcknowledge(String topic, MqttMessage message) {
        try {
            handleMessage(topic, message);
            mqttClient.messageArrivedComplete(message.getId(), message.getQos());
        } catch (RuntimeException | MqttException e) {
            // Deliberately omit messageArrivedComplete: a persistent session
            // retains/redelivers the record after a transient failure.
            log.error("ingest failed for topic {}; MQTT message left unacknowledged", topic, e);
        }
    }

    @PreDestroy
    void stopWorker() {
        ingestWorker.shutdown();
    }

    private static DeviceId extractDeviceId(String topic) {
        String[] segments = topic.split("/");
        return new DeviceId(segments[2]);
    }
}
