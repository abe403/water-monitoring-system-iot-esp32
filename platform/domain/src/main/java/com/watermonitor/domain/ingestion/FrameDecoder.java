package com.watermonitor.domain.ingestion;

import com.watermonitor.domain.telemetry.TelemetryRecord;

import java.util.List;

public interface FrameDecoder {

    boolean supports(WireFormat format);

    List<TelemetryRecord> decode(RawBatch batch) throws DecodeException;
}
