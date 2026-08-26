package com.watermonitor.sink.adapter;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class ReadingJdbcRepository {

    private final JdbcClient jdbc;

    public ReadingJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String deviceId, long bootId, long seq,
                       Instant observedAt, Instant receivedAt,
                       Short distanceMm, Short tempTenths, Short rssiDbm,
                       Double levelPct, String quality, int wireVersion) {

        jdbc.sql("""
                WITH seen_device AS (
                    INSERT INTO device (device_id, last_seen_at)
                    VALUES (?, ?)
                    ON CONFLICT (device_id) DO UPDATE
                    SET last_seen_at = GREATEST(device.last_seen_at, EXCLUDED.last_seen_at)
                ), accepted AS (
                    INSERT INTO reading_ingest_key (device_id, boot_id, seq, observed_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (device_id, boot_id, seq) DO NOTHING
                    RETURNING device_id, boot_id, seq
                )
                INSERT INTO reading (device_id, boot_id, seq, observed_at, received_at,
                                     distance_mm, temp_tenths, rssi_dbm, level_pct,
                                     quality, wire_version)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                FROM accepted
                """)
                .param(deviceId)
                .param(Timestamp.from(receivedAt))
                .param(deviceId)
                .param(bootId)
                .param(seq)
                .param(Timestamp.from(observedAt))
                .param(deviceId)
                .param(bootId)
                .param(seq)
                .param(Timestamp.from(observedAt))
                .param(Timestamp.from(receivedAt))
                .param(distanceMm)
                .param(tempTenths)
                .param(rssiDbm)
                .param(levelPct)
                .param(quality)
                .param(wireVersion)
                .update();
    }
}
