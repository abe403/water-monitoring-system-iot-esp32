package com.watermonitor.domain.ingestion;

/** A deterministic decode failure — schema mismatch, bad checksum, unknown wire format. Never transient. */
public class DecodeException extends Exception {

    public DecodeException(String message) {
        super(message);
    }

    public DecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
