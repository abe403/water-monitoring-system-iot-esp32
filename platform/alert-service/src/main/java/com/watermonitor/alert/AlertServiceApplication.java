package com.watermonitor.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owns the {@link com.watermonitor.domain.alert.Alert} aggregate's
 * persistence and lifecycle: consumes {@code anomaly.scored.v1}, applies
 * dedup/suppression, persists transitions, and publishes to
 * {@code alerts.v1}. WebSocket fan-out to {@code operations-api} should use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} rather than
 * publishing inline — pushing an alert state the database transaction then
 * rolls back is a correctness bug, not a cosmetic one. See
 * docs/ARCHITECTURE.md, "where OOD is the wrong tool" for why this service's
 * alert lifecycle uses a plain enum + transition table
 * ({@link com.watermonitor.domain.alert.Alert}) rather than the GoF State
 * pattern.
 */
@SpringBootApplication
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}
