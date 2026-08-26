package com.watermonitor.streamprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watermonitor.domain.calibration.CalibrationStrategy;
import com.watermonitor.domain.calibration.LinearTwoPointCalibration;
import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.LevelPercent;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Instant;

/**
 * Applies server-side calibration and quality flagging. The calibration
 * strategy is currently hardcoded (the tank geometry from the user's
 * existing installation); a production evolution looks it up from a KTable
 * of {@code calibration_profile} keyed by device, using
 * {@code observedAt} to find the effective profile at measurement time
 * (not wall-clock time — see CalibrationProfileRepository's Javadoc).
 */
public final class TelemetryEnrichmentTransformer
        implements ValueTransformerWithKey<String, byte[], byte[]> {

    private static final String LAST_SEEN_STORE = "device-last-seen";
    private static final CalibrationStrategy DEFAULT_CALIBRATION =
            new LinearTwoPointCalibration(
                    Distance.ofMillimetres(200),
                    Distance.ofMillimetres(720));

    private KeyValueStore<String, Long> lastSeenStore;
    private final ObjectMapper json;

    public TelemetryEnrichmentTransformer(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void init(ProcessorContext context) {
        this.lastSeenStore = context.getStateStore(LAST_SEEN_STORE);
    }

    @Override
    public byte[] transform(String deviceId, byte[] rawValue) {
        try {
            JsonNode node = json.readTree(rawValue);

            Instant observedAt = Instant.ofEpochMilli(node.get("observedAtEpochMs").asLong());
            Instant receivedAt = Instant.ofEpochMilli(node.get("ingestedAtEpochMs").asLong());
            long bootId = node.get("bootId").asLong();
            long seq = node.get("seq").asLong();

            double distanceM = node.get("distanceM").asDouble();
            short distanceMm = (short) Math.round(distanceM * 1000.0);
            Double tempC = node.has("temperatureC") && !node.get("temperatureC").isNull()
                    ? node.get("temperatureC").asDouble() : null;
            Short tempTenths = tempC != null ? (short) Math.round(tempC * 10.0) : null;
            short rssiDbm = (short) node.get("rssiDbm").asInt();
            int wireVersion = 1;

            Double levelPct = null;
            String quality = "GOOD";
            Distance d = Distance.ofMillimetres(distanceMm);
            LevelPercent level = DEFAULT_CALIBRATION.toLevel(d);
            levelPct = level.value();

            if (distanceMm < 100 || distanceMm > 2000) {
                quality = "SUSPECT";
            }

            long interarrivalMs = 0;
            Long prev = lastSeenStore.get(deviceId);
            if (prev != null) {
                interarrivalMs = observedAt.toEpochMilli() - prev;
            }
            lastSeenStore.put(deviceId, observedAt.toEpochMilli());

            EnrichedTelemetryDto enriched = new EnrichedTelemetryDto(
                    deviceId, bootId, seq, observedAt, receivedAt,
                    distanceMm, tempTenths, rssiDbm, levelPct,
                    quality, wireVersion, interarrivalMs);

            return json.writeValueAsBytes(enriched);

        } catch (Exception e) {
            throw new RuntimeException("enrichment failed for device " + deviceId, e);
        }
    }

    @Override
    public void close() {
    }

}
