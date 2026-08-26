package com.watermonitor.api.controller;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final JdbcClient jdbc;

    public AlertController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> getActiveAlerts() {
        return jdbc.sql("""
                SELECT id, device_id, anomaly_type, state, score, opened_at, updated_at
                FROM alert
                WHERE state NOT IN ('RESOLVED', 'EXPIRED')
                ORDER BY opened_at DESC
                """)
                .query().listOfRows();
    }

    @GetMapping("/{deviceId}")
    public List<Map<String, Object>> getAlertsByDevice(@PathVariable String deviceId) {
        return jdbc.sql("""
                SELECT id, anomaly_type, state, score, opened_at, updated_at, resolved_at, resolved_by
                FROM alert
                WHERE device_id = ?
                ORDER BY opened_at DESC
                LIMIT 100
                """)
                .param(deviceId)
                .query().listOfRows();
    }

    @PostMapping("/{alertId}/acknowledge")
    public Map<String, Object> acknowledge(@PathVariable UUID alertId) {
        int updated = jdbc.sql("""
                UPDATE alert SET state = 'ACKNOWLEDGED', updated_at = ?
                WHERE id = ? AND state = 'OPEN'
                """)
                .param(Timestamp.from(Instant.now()))
                .param(alertId)
                .update();

        return Map.of("updated", updated > 0, "alertId", alertId);
    }

    @PostMapping("/{alertId}/resolve")
    public Map<String, Object> resolve(@PathVariable UUID alertId) {
        Instant now = Instant.now();
        int updated = jdbc.sql("""
                UPDATE alert SET state = 'RESOLVED', resolved_at = ?, resolved_by = 'operator', updated_at = ?
                WHERE id = ? AND state IN ('OPEN', 'ACKNOWLEDGED')
                """)
                .param(Timestamp.from(now))
                .param(Timestamp.from(now))
                .param(alertId)
                .update();

        return Map.of("updated", updated > 0, "alertId", alertId);
    }
}
