CREATE TABLE IF NOT EXISTS device (
    device_id         TEXT PRIMARY KEY,
    hardware_revision TEXT,
    firmware_version  TEXT,
    provisioned       BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
