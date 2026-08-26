CREATE MATERIALIZED VIEW IF NOT EXISTS reading_1min
WITH (timescaledb.continuous) AS
SELECT
    device_id,
    time_bucket('1 minute', observed_at) AS bucket,
    avg(distance_mm)  AS avg_distance_mm,
    avg(temp_tenths)   AS avg_temp_tenths,
    avg(rssi_dbm)      AS avg_rssi_dbm,
    avg(level_pct)     AS avg_level_pct,
    count(*)           AS sample_count
FROM reading
GROUP BY device_id, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy('reading_1min',
    start_offset  => INTERVAL '1 hour',
    end_offset    => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => TRUE);

CREATE MATERIALIZED VIEW IF NOT EXISTS reading_1hr
WITH (timescaledb.continuous) AS
SELECT
    device_id,
    time_bucket('1 hour', observed_at) AS bucket,
    avg(distance_mm)  AS avg_distance_mm,
    avg(temp_tenths)   AS avg_temp_tenths,
    avg(rssi_dbm)      AS avg_rssi_dbm,
    avg(level_pct)     AS avg_level_pct,
    min(level_pct)     AS min_level_pct,
    max(level_pct)     AS max_level_pct,
    count(*)           AS sample_count
FROM reading
GROUP BY device_id, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy('reading_1hr',
    start_offset  => INTERVAL '1 day',
    end_offset    => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE);
