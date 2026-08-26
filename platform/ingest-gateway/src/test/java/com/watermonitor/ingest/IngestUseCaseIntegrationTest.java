package com.watermonitor.ingest;

import com.watermonitor.domain.device.BootId;
import com.watermonitor.domain.device.Device;
import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.device.Sequence;
import com.watermonitor.domain.ingestion.DecodeException;
import com.watermonitor.domain.ingestion.FrameDecoderFactory;
import com.watermonitor.domain.ingestion.IngestOutcome;
import com.watermonitor.domain.ingestion.IngestTelemetryBatchUseCase;
import com.watermonitor.domain.ingestion.RawBatch;
import com.watermonitor.domain.ingestion.WireFormat;
import com.watermonitor.domain.ports.AckPort;
import com.watermonitor.domain.ports.DeadLetterPort;
import com.watermonitor.domain.ports.DeviceRegistryPort;
import com.watermonitor.domain.ports.PublishReceipt;
import com.watermonitor.domain.ports.TelemetryPublisherPort;
import com.watermonitor.domain.telemetry.TelemetryRecord;
import com.watermonitor.ingest.adapter.TelemetryOutboxV1Decoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full ingest flow with in-memory port implementations:
 * decode a real wire frame → publish → wait → ack. No Spring context,
 * no broker, no Kafka — pure unit test of the domain's durability contract.
 */
class IngestUseCaseIntegrationTest {

    private final List<TelemetryRecord> published = new ArrayList<>();
    private final List<AckRecord> acked = new ArrayList<>();
    private final List<RawBatch> deadLettered = new ArrayList<>();

    private IngestTelemetryBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        DeviceRegistryPort registry = id -> Optional.of(
                new Device(id, "rev3", "2026.8.1", true));

        TelemetryPublisherPort publisher = record -> {
            published.add(record);
            return CompletableFuture.completedFuture(
                    new PublishReceipt(record.idempotencyKey(), 42L, 0));
        };

        AckPort ackPort = (deviceId, bootId, seq) ->
                acked.add(new AckRecord(deviceId, bootId, seq));

        DeadLetterPort dlq = (batch, reason) -> deadLettered.add(batch);

        FrameDecoderFactory decoders = new FrameDecoderFactory(
                List.of(new TelemetryOutboxV1Decoder()));

        useCase = new IngestTelemetryBatchUseCase(decoders, registry, publisher, ackPort, dlq);
    }

    @Test
    void happyPath_decodesPublishesAndAcks() {
        byte[] frame = buildFrame(1, 7, 1724400000L, (short) 350, (short) 245, (short) -62, (short) 0);

        RawBatch batch = new RawBatch(
                new DeviceId("tank-01"), WireFormat.TELEMETRY_OUTBOX_V1, frame, Instant.now());

        IngestOutcome outcome = useCase.handle(batch);

        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.ACCEPTED);
        assertThat(outcome.recordCount()).isEqualTo(1);
        assertThat(published).hasSize(1);

        TelemetryRecord record = published.getFirst();
        assertThat(record.deviceId().value()).isEqualTo("tank-01");
        assertThat(record.bootId().value()).isEqualTo(1L);
        assertThat(record.seq().value()).isEqualTo(7L);

        assertThat(acked).hasSize(1);
        assertThat(acked.getFirst().highestSeq().value()).isEqualTo(7L);
    }

    @Test
    void poisonPayload_goesToDeadLetterQueue() {
        byte[] tooShort = new byte[]{0x01, 0x02, 0x03};

        RawBatch batch = new RawBatch(
                new DeviceId("tank-01"), WireFormat.TELEMETRY_OUTBOX_V1, tooShort, Instant.now());

        IngestOutcome outcome = useCase.handle(batch);

        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.POISON);
        assertThat(published).isEmpty();
        assertThat(acked).isEmpty();
        assertThat(deadLettered).hasSize(1);
    }

    @Test
    void publishFailure_doesNotAck() {
        TelemetryPublisherPort failingPublisher = record -> {
            CompletableFuture<PublishReceipt> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("Kafka down"));
            return f;
        };

        FrameDecoderFactory decoders = new FrameDecoderFactory(
                List.of(new TelemetryOutboxV1Decoder()));
        DeviceRegistryPort registry = id -> Optional.of(
                new Device(id, "rev3", "2026.8.1", true));
        AckPort ackPort = (deviceId, bootId, seq) ->
                acked.add(new AckRecord(deviceId, bootId, seq));
        DeadLetterPort dlq = (batch, reason) -> deadLettered.add(batch);

        var failingUseCase = new IngestTelemetryBatchUseCase(
                decoders, registry, failingPublisher, ackPort, dlq);

        byte[] frame = buildFrame(1, 1, 1724400000L, (short) 350, (short) 245, (short) -62, (short) 0);
        RawBatch batch = new RawBatch(
                new DeviceId("tank-01"), WireFormat.TELEMETRY_OUTBOX_V1, frame, Instant.now());

        try {
            failingUseCase.handle(batch);
        } catch (Exception expected) {
            // The use case must propagate the exception so the MQTT message
            // is NOT acknowledged — this is the durability guarantee.
        }

        assertThat(acked).isEmpty();
    }

    private static byte[] buildFrame(long bootId, long seq, long epochS,
                                      short distanceMm, short tempTenths,
                                      short rssiDbm, short checksumErrors) {
        return ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) bootId)
                .putInt((int) seq)
                .putInt((int) epochS)
                .putShort(distanceMm)
                .putShort(tempTenths)
                .putShort(rssiDbm)
                .putShort(checksumErrors)
                .array();
    }

    record AckRecord(DeviceId deviceId, BootId bootId, Sequence highestSeq) {}
}
