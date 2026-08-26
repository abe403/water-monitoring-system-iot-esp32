package com.watermonitor.sink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Consumes {@code telemetry.enriched.v1} and idempotently upserts into
 * TimescaleDB. A regular deduplication table enforces the
 * {@code (device_id, boot_id, seq)} key defined by
 * {@link com.watermonitor.domain.device.IdempotencyKey}, because Timescale
 * unique indexes must include the hypertable's time partition column.
 * Kafka offsets must commit only after the database transaction commits, not
 * before; getting that ordering backwards is how at-least-once delivery
 * turns into silent loss on a crash between the two commits. See
 * docs/ARCHITECTURE.md, "the durability chain".
 */
@SpringBootApplication
public class TimescaleSinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimescaleSinkApplication.class, args);
    }
}
