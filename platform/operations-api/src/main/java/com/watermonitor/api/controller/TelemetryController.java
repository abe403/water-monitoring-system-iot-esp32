package com.watermonitor.api.controller;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final JdbcClient jdbc;

    public TelemetryController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{deviceId}")
    public List<Map<String, Object>> getReadings(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "1") int hours) {

        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        return jdbc.sql("""
                SELECT device_id, boot_id, seq, observed_at, received_at,
                       distance_mm, temp_tenths, rssi_dbm, level_pct, quality
                FROM reading
                WHERE device_id = ? AND observed_at >= ?
                ORDER BY observed_at DESC
                LIMIT 1000
                """)
                .param(deviceId)
                .param(Timestamp.from(since))
                .query().listOfRows();
    }

    @GetMapping("/{deviceId}/hourly")
    public List<Map<String, Object>> getHourlyAggregates(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24") int hours) {

        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        return jdbc.sql("""
                SELECT device_id, bucket, avg_distance_mm, avg_temp_tenths,
                       avg_rssi_dbm, avg_level_pct, min_level_pct, max_level_pct,
                       sample_count
                FROM reading_1hr
                WHERE device_id = ? AND bucket >= ?
                ORDER BY bucket DESC
                """)
                .param(deviceId)
                .param(Timestamp.from(since))
                .query().listOfRows();
    }
}
