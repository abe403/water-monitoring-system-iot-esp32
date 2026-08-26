package com.watermonitor.domain.ports;

import com.watermonitor.domain.ingestion.DecodeException;
import com.watermonitor.domain.ingestion.RawBatch;

/**
 * Where deterministically-unprocessable ("poison") messages go — schema or
 * decode failures that no amount of retrying will fix. Transient failures
 * (a database or broker being temporarily down) must never come here; they
 * are retried until they succeed. Routing a transient failure to the dead
 * letter queue looks like resilience and is actually silent data loss. See
 * docs/ARCHITECTURE.md, "the durability chain".
 */
public interface DeadLetterPort {

    void publish(RawBatch batch, DecodeException reason);
}
