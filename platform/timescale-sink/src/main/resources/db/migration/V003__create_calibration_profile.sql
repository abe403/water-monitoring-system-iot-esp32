CREATE TABLE IF NOT EXISTS calibration_profile (
    id             BIGSERIAL   PRIMARY KEY,
    device_id      TEXT        NOT NULL REFERENCES device(device_id),
    strategy       TEXT        NOT NULL,
    params         JSONB       NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,
    version        INT         NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_calibration_device_effective
    ON calibration_profile (device_id, effective_from DESC);
