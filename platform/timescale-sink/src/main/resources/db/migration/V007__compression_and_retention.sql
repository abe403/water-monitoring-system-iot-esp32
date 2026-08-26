ALTER TABLE reading SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'device_id',
    timescaledb.compress_orderby = 'observed_at DESC'
);

SELECT add_compression_policy('reading', INTERVAL '7 days', if_not_exists => TRUE);

SELECT add_retention_policy('reading', INTERVAL '2 years', if_not_exists => TRUE);
