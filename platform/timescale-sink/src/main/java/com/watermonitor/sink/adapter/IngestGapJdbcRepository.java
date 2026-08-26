package com.watermonitor.sink.adapter;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestGapJdbcRepository {

    private final JdbcClient jdbc;

    public IngestGapJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String deviceId, long bootId, long missingFrom, long missingTo, long gapSize) {
        jdbc.sql("""
                INSERT INTO ingest_gap
                    (device_id, boot_id, expected_seq, actual_seq, gap_size)
                VALUES (?, ?, ?, ?, ?)
                """)
                .param(deviceId)
                .param(bootId)
                .param(missingFrom)
                .param(missingTo + 1)
                .param(gapSize)
                .update();
    }
}
