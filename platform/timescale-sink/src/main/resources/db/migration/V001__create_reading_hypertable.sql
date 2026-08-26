-- Timescale unique indexes must include the time partitioning column. Keep
-- the transport idempotency key in a small regular table, then gate inserts
-- into the hypertable through it in one statement (see ReadingJdbcRepository).
CREATE TABLE IF NOT EXISTS reading_ingest_key (
    device_id   TEXT        NOT NULL,
    boot_id     BIGINT      NOT NULL,
    seq         BIGINT      NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_id, boot_id, seq)
);

CREATE TABLE IF NOT EXISTS reading (
    device_id    TEXT        NOT NULL,
    boot_id      BIGINT      NOT NULL,
    seq          BIGINT      NOT NULL,
    observed_at  TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    distance_mm  SMALLINT,
    temp_tenths  SMALLINT,
    rssi_dbm     SMALLINT,
    level_pct    DOUBLE PRECISION,
    quality      TEXT        NOT NULL DEFAULT 'GOOD',
    wire_version SMALLINT    NOT NULL DEFAULT 1
);

SELECT create_hypertable('reading', by_range('observed_at'),
    if_not_exists => TRUE,
    migrate_data  => TRUE);

CREATE INDEX IF NOT EXISTS idx_reading_device_time
    ON reading (device_id, observed_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_transport_key
    ON reading (device_id, boot_id, seq);
