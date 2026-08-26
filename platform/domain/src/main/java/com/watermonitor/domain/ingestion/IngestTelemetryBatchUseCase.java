package com.watermonitor.domain.ingestion;

import com.watermonitor.domain.device.Device;
import com.watermonitor.domain.ports.AckPort;
import com.watermonitor.domain.ports.DeadLetterPort;
import com.watermonitor.domain.ports.DeviceRegistryPort;
import com.watermonitor.domain.ports.PublishReceipt;
import com.watermonitor.domain.ports.TelemetryPublisherPort;
import com.watermonitor.domain.telemetry.TelemetryRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The driving port's implementation: everything that happens between "an MQTT
 * message arrived" and "the device has been told it is safe to forget this
 * data". The ordering in {@link #handle} — publish, wait, THEN acknowledge —
 * is the entire durability contract in code form. See
 * docs/ARCHITECTURE.md, "the durability chain".
 */
public final class IngestTelemetryBatchUseCase {

    private final FrameDecoderFactory decoders;
    private final DeviceRegistryPort registry;
    private final TelemetryPublisherPort publisher;
    private final AckPort ack;
    private final DeadLetterPort deadLetters;

    public IngestTelemetryBatchUseCase(
            FrameDecoderFactory decoders,
            DeviceRegistryPort registry,
            TelemetryPublisherPort publisher,
            AckPort ack,
            DeadLetterPort deadLetters) {
        this.decoders = decoders;
        this.registry = registry;
        this.publisher = publisher;
        this.ack = ack;
        this.deadLetters = deadLetters;
    }

    public IngestOutcome handle(RawBatch batch) {
        Device device = registry.findById(batch.deviceId())
                .orElseThrow(() -> new UnknownDevice(batch.deviceId()));

        List<TelemetryRecord> records;
        try {
            records = decoders.forFormat(batch.wireFormat()).decode(batch);
        } catch (DecodeException e) {
            // Poison: a deterministic failure. Retrying cannot help, so this
            // goes to the dead letter queue immediately rather than being
            // retried — see DeadLetterPort's Javadoc for why the opposite
            // choice (retrying a transient failure into the DLQ) is wrong.
            deadLetters.publish(batch, e);
            return IngestOutcome.poison();
        }
        if (records.isEmpty()) {
            return new IngestOutcome(IngestOutcome.Result.ACCEPTED, 0, java.util.Optional.empty());
        }

        List<CompletionStage<PublishReceipt>> receipts = records.stream()
                .map(publisher::publish)
                .toList();

        // Blocks until every record in this batch is durably in Kafka. A
        // transient failure here (broker down, timeout) throws and the
        // caller must NOT acknowledge the device — the MQTT message is
        // redelivered and this method runs again. This is deliberate
        // backpressure, not a bug: it is what stops a struggling downstream
        // from ever causing silent loss upstream.
        CompletableFuture.allOf(receipts.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                .join();

        var highestSeq = records.get(records.size() - 1).seq();
        ack.acknowledgeThrough(device.id(), records.get(0).bootId(), highestSeq);

        return IngestOutcome.accepted(records.size(), highestSeq);
    }
}
