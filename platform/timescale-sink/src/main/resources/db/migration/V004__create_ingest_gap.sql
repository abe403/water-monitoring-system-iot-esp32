CREATE TABLE IF NOT EXISTS ingest_gap (
    id          BIGSERIAL   PRIMARY KEY,
    device_id   TEXT        NOT NULL,
    boot_id     BIGINT      NOT NULL,
    expected_seq BIGINT     NOT NULL,
    actual_seq  BIGINT      NOT NULL,
    gap_size    INT         NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gap_device
    ON ingest_gap (device_id, detected_at DESC);
