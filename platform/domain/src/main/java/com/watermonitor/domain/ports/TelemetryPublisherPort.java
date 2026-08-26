package com.watermonitor.domain.ports;

import com.watermonitor.domain.telemetry.TelemetryRecord;

import java.util.concurrent.CompletionStage;

/**
 * The durability contract, stated once, here, in the domain — not scattered
 * across configuration. An adapter implementing this port (in
 * {@code ingest-gateway}) MUST NOT complete the returned stage until the
 * record is durably replicated: {@code acks=all} with the in-sync replica
 * count at or above {@code min.insync.replicas}. Completing early — e.g. on
 * local buffering, or on a leader-only ack — silently breaks the project's
 * zero-loss claim for every caller of this port, including the code that
 * acknowledges the device. See docs/ARCHITECTURE.md, "the durability chain".
 */
public interface TelemetryPublisherPort {

    CompletionStage<PublishReceipt> publish(TelemetryRecord record);
}
