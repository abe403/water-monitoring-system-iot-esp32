package com.watermonitor.ingest.adapter;

import com.watermonitor.domain.device.BootId;
import com.watermonitor.domain.device.Sequence;
import com.watermonitor.domain.ingestion.DecodeException;
import com.watermonitor.domain.ingestion.FrameDecoder;
import com.watermonitor.domain.ingestion.RawBatch;
import com.watermonitor.domain.ingestion.WireFormat;
import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.Quality;
import com.watermonitor.domain.telemetry.Reading;
import com.watermonitor.domain.telemetry.SignalStrength;
import com.watermonitor.domain.telemetry.Temperature;
import com.watermonitor.domain.telemetry.TelemetryRecord;
import com.watermonitor.domain.telemetry.TransportMetadata;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the 20-byte packed record produced by
 * {@code firmware/components/telemetry_outbox}. Byte layout must match
 * {@code TelemetryRecord} in {@code telemetry_outbox.h} exactly — see that
 * file's {@code static_assert} for the tripwire on the firmware side. This
 * class is the tripwire on the server side: {@link #decode} rejects any
 * payload that is not exactly {@link #RECORD_SIZE_BYTES} long rather than
 * guessing at a partial or extended layout.
 *
 * <p>The wire is little-endian with no byte-swap, matching both the ESP32
 * and typical server hardware — see contracts/wire-format.md.
 *
 * <p>One MQTT message from the outbox is exactly one record (the firmware
 * does not batch), so this decoder always returns a single-element list.
 */
@Component
public final class TelemetryOutboxV1Decoder implements FrameDecoder {

    private static final int RECORD_SIZE_BYTES = 20;
    private static final short NO_TEMPERATURE_READING = Short.MIN_VALUE;

    @Override
    public boolean supports(WireFormat format) {
        return format.equals(WireFormat.TELEMETRY_OUTBOX_V1);
    }

    @Override
    public List<TelemetryRecord> decode(RawBatch batch) throws DecodeException {
        byte[] payload = batch.payload();
        if (payload.length != RECORD_SIZE_BYTES) {
            throw new DecodeException(
                    "expected a %d-byte telemetry_outbox v1 record, got %d bytes from device %s"
                            .formatted(RECORD_SIZE_BYTES, payload.length, batch.deviceId()));
        }

        ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        long bootId = Integer.toUnsignedLong(buf.getInt());
        long seq = Integer.toUnsignedLong(buf.getInt());
        long epochSeconds = Integer.toUnsignedLong(buf.getInt());
        short distanceMm = buf.getShort();
        short tempTenthsC = buf.getShort();
        short rssiDbm = buf.getShort();
        int checksumErrors = Short.toUnsignedInt(buf.getShort());

        Instant observedAt = epochSeconds == 0
                ? batch.receivedAt() // device had not yet synced SNTP; best available estimate
                : Instant.ofEpochSecond(epochSeconds);

        List<Reading> readings = new ArrayList<>(3);
        readings.add(new Reading(Distance.ofMillimetres(distanceMm), Quality.GOOD, observedAt));
        readings.add(new Reading(new SignalStrength(rssiDbm), Quality.GOOD, observedAt));
        if (tempTenthsC != NO_TEMPERATURE_READING) {
            readings.add(new Reading(Temperature.ofTenthsCelsius(tempTenthsC), Quality.GOOD, observedAt));
        }

        TelemetryRecord record = new TelemetryRecord(
                batch.deviceId(),
                new BootId(bootId),
                new Sequence(seq),
                observedAt,
                batch.receivedAt(),
                readings,
                new TransportMetadata(WireFormat.TELEMETRY_OUTBOX_V1.version(), checksumErrors));

        return List.of(record);
    }
}
