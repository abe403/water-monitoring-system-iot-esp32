package com.watermonitor.domain.ingestion;

import com.watermonitor.domain.device.Sequence;

import java.util.Optional;

public record IngestOutcome(Result result, int recordCount, Optional<Sequence> highestSeq) {

    public enum Result {
        ACCEPTED,
        POISON,
    }

    public static IngestOutcome accepted(int recordCount, Sequence highestSeq) {
        return new IngestOutcome(Result.ACCEPTED, recordCount, Optional.of(highestSeq));
    }

    /** The batch could not be decoded, so its sequence numbers are unknown. */
    public static IngestOutcome poison() {
        return new IngestOutcome(Result.POISON, 0, Optional.empty());
    }
}
