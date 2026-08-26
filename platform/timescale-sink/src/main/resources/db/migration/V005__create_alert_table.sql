CREATE TABLE IF NOT EXISTS alert (
    id           UUID        PRIMARY KEY,
    device_id    TEXT        NOT NULL,
    anomaly_type TEXT        NOT NULL,
    state        TEXT        NOT NULL DEFAULT 'OPEN',
    score        DOUBLE PRECISION,
    opened_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    resolved_by  TEXT
);

CREATE INDEX IF NOT EXISTS idx_alert_device_state
    ON alert (device_id, state) WHERE state != 'RESOLVED';
