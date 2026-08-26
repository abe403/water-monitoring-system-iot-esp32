package com.watermonitor.ingest.adapter;

import com.watermonitor.domain.telemetry.Distance;
import com.watermonitor.domain.telemetry.SignalStrength;
import com.watermonitor.domain.telemetry.TelemetryRecord;
import com.watermonitor.domain.telemetry.Temperature;

/**
 * The wire shape actually written to {@code telemetry.raw.v1}. A flat DTO
 * rather than serializing {@link TelemetryRecord} directly: the domain's
 * {@code Quantity} sealed hierarchy is deliberately not annotated for
 * polymorphic JSON (that would leak a serialization framework's concerns
 * into the domain), so mapping happens once, here, at the adapter boundary —
 * the same anti-corruption-layer principle applied to Avro-generated types
 * in docs/ARCHITECTURE.md.
 *
 * <p>JSON today; contracts/avro/telemetry.raw.v1.avsc defines the intended
 * Avro schema once Schema Registry is wired up (plan M3) — this DTO's fields
 * are already named to match it 1:1 so that migration is a serializer swap,
 * not a field redesign.
 */
public record TelemetryEventDto(
        String deviceId,
        long bootId,
        long seq,
        long observedAtEpochMs,
        long ingestedAtEpochMs,
        double distanceM,
        Double temperatureC, // null when the device had no temperature reading
        double rssiDbm,
        int checksumErrorsAtReceipt) {

    public static TelemetryEventDto from(TelemetryRecord record) {
        Double temperatureC = null;
        try {
            temperatureC = ((Temperature) record.readingOf(Temperature.class).value()).celsius();
        } catch (IllegalStateException noTemperatureReading) {
            // expected when the device's NTC reading was unavailable this cycle
        }

        return new TelemetryEventDto(
                record.deviceId().value(),
                record.bootId().value(),
                record.seq().value(),
                record.observedAt().toEpochMilli(),
                record.ingestedAt().toEpochMilli(),
                ((Distance) record.readingOf(Distance.class).value()).metres(),
                temperatureC,
                ((SignalStrength) record.readingOf(SignalStrength.class).value()).dBm(),
                record.transport().checksumErrorsAtReceipt());
    }
}
