/**
 * MQTT-to-Kafka ingest gateway — the durability chain entry point.
 *
 * <p>All domain ports are now wired to concrete adapters:
 * <ul>
 *   <li>{@link com.watermonitor.ingest.adapter.MqttTelemetryInboundAdapter} — subscribes to
 *       {@code wtm/v1/+/tel}, hands each message to the domain use case.</li>
 *   <li>{@link com.watermonitor.ingest.adapter.TelemetryOutboxV1Decoder} — decodes the
 *       firmware's 20-byte wire format.</li>
 *   <li>{@link com.watermonitor.ingest.adapter.KafkaTelemetryPublisherAdapter} — acks=all,
 *       idempotent producer (see {@link com.watermonitor.ingest.config.DurableKafkaProducerConfig}).</li>
 *   <li>{@link com.watermonitor.ingest.adapter.MqttAckAdapter} — publishes the application-level
 *       ack to {@code wtm/v1/<id>/ack} after Kafka confirms.</li>
 *   <li>{@link com.watermonitor.ingest.adapter.KafkaDeadLetterAdapter} — routes poison messages
 *       to {@code telemetry.dlq.v1}.</li>
 *   <li>{@link com.watermonitor.ingest.adapter.InMemoryDeviceRegistryAdapter} — auto-provisions
 *       any device on first contact (MVP; production uses a compacted topic).</li>
 * </ul>
 */
package com.watermonitor.ingest;
