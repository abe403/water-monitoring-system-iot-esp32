package com.watermonitor.domain.ingestion;

public class UnsupportedWireFormat extends RuntimeException {

    public UnsupportedWireFormat(WireFormat format) {
        super("no decoder registered for wire format version " + format.version());
    }
}
