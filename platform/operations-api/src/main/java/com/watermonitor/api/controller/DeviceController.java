package com.watermonitor.api.controller;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final JdbcClient jdbc;

    public DeviceController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> listDevices() {
        return jdbc.sql("""
                SELECT d.device_id, d.hardware_revision, d.firmware_version,
                       d.provisioned, d.first_seen_at, d.last_seen_at,
                       (SELECT count(*) FROM reading r WHERE r.device_id = d.device_id) AS total_readings,
                       (SELECT count(*) FROM alert a WHERE a.device_id = d.device_id
                        AND a.state NOT IN ('RESOLVED', 'EXPIRED')) AS active_alerts
                FROM device d
                ORDER BY d.last_seen_at DESC
                """)
                .query().listOfRows();
    }

    @GetMapping("/{deviceId}")
    public Map<String, Object> getDevice(@PathVariable String deviceId) {
        return jdbc.sql("""
                SELECT device_id, hardware_revision, firmware_version,
                       provisioned, first_seen_at, last_seen_at
                FROM device
                WHERE device_id = ?
                """)
                .param(deviceId)
                .query().singleRow();
    }

    @GetMapping("/{deviceId}/gaps")
    public List<Map<String, Object>> getGaps(@PathVariable String deviceId) {
        return jdbc.sql("""
                SELECT boot_id, expected_seq, actual_seq, gap_size, detected_at
                FROM ingest_gap
                WHERE device_id = ?
                ORDER BY detected_at DESC
                LIMIT 100
                """)
                .param(deviceId)
                .query().listOfRows();
    }
}
