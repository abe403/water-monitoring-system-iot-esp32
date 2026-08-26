package com.watermonitor.ingest;

import com.watermonitor.domain.device.DeviceId;
import com.watermonitor.domain.ingestion.RawBatch;
import com.watermonitor.domain.ingestion.WireFormat;
import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.TelemetryRecord;
import com.watermonitor.domain.telemetry.Temperature;
import com.watermonitor.ingest.adapter.TelemetryOutboxV1Decoder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden-vector cross-check: these exact bytes are also tested in
 * {@code simulator/tests/test_wire.py} as the Python encoder's output for
 * the same inputs. If either side changes the wire layout, the other's test
 * will fail — that is the mechanism preventing silent encoding drift.
 *
 * <p>The 20-byte little-endian format:
 * <pre>
 *   [0:4]   uint32  boot_id
 *   [4:8]   uint32  seq
 *   [8:12]  uint32  epoch_s
 *   [12:14] int16   distance_mm
 *   [14:16] int16   temp_tenths_c
 *   [16:18] int16   rssi_dbm
 *   [18:20] uint16  checksum_errors
 * </pre>
 */
class WireFormatCrossCheckTest {

    private final TelemetryOutboxV1Decoder decoder = new TelemetryOutboxV1Decoder();

    @Test
    void goldenVector_matchesSimulatorEncodedBytes() throws Exception {
        // boot_id=1, seq=42, epoch_s=1724400000, distance_mm=350,
        // temp_tenths=245 (24.5°C), rssi_dbm=-62, checksum_errors=0
        byte[] golden = ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1)           // boot_id
                .putInt(42)          // seq
                .putInt(1724400000)  // epoch_s (2024-08-23T08:00:00Z)
                .putShort((short) 350)   // distance_mm
                .putShort((short) 245)   // temp_tenths (24.5°C)
                .putShort((short) -62)   // rssi_dbm
                .putShort((short) 0)     // checksum_errors
                .array();

        assertThat(golden).hasSize(20);

        RawBatch batch = new RawBatch(
                new DeviceId("test-device"), WireFormat.TELEMETRY_OUTBOX_V1,
                golden, Instant.now());

        List<TelemetryRecord> records = decoder.decode(batch);
        assertThat(records).hasSize(1);

        TelemetryRecord r = records.getFirst();
        assertThat(r.bootId().value()).isEqualTo(1L);
        assertThat(r.seq().value()).isEqualTo(42L);
        assertThat(r.observedAt()).isEqualTo(Instant.ofEpochSecond(1724400000L));

        Distance dist = (Distance) r.readingOf(Distance.class).value();
        assertThat(dist.metres()).isCloseTo(0.350, within(0.001));

        Temperature temp = (Temperature) r.readingOf(Temperature.class).value();
        assertThat(temp.celsius()).isCloseTo(24.5, within(0.1));
    }

    @Test
    void epochZeroSentinel_usesReceivedAtTimestamp() throws Exception {
        byte[] frame = ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(5)           // boot_id
                .putInt(1)           // seq
                .putInt(0)           // epoch_s = 0 (SNTP not yet synced)
                .putShort((short) 400)
                .putShort((short) Short.MIN_VALUE)  // no temperature reading
                .putShort((short) -70)
                .putShort((short) 3)
                .array();

        Instant receivedAt = Instant.parse("2026-08-23T12:00:00Z");
        RawBatch batch = new RawBatch(
                new DeviceId("tank-02"), WireFormat.TELEMETRY_OUTBOX_V1,
                frame, receivedAt);

        List<TelemetryRecord> records = decoder.decode(batch);
        TelemetryRecord r = records.getFirst();

        assertThat(r.observedAt()).isEqualTo(receivedAt);
        assertThat(r.readings()).hasSize(2); // distance + rssi only, no temp
    }

    @Test
    void wrongSize_throwsDecodeException() {
        byte[] tooShort = new byte[19];
        RawBatch batch = new RawBatch(
                new DeviceId("tank-03"), WireFormat.TELEMETRY_OUTBOX_V1,
                tooShort, Instant.now());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.watermonitor.domain.ingestion.DecodeException.class,
                () -> decoder.decode(batch));
    }
}
