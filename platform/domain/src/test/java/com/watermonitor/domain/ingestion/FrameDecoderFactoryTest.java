package com.watermonitor.domain.ingestion;

import com.watermonitor.domain.telemetry.TelemetryRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameDecoderFactoryTest {

    private static final WireFormat V1 = new WireFormat(1);
    private static final WireFormat V2 = new WireFormat(2);
    private static final WireFormat UNKNOWN = new WireFormat(99);

    private final FakeDecoder v1Decoder = new FakeDecoder(V1);
    private final FakeDecoder v2Decoder = new FakeDecoder(V2);
    private final FrameDecoderFactory factory = new FrameDecoderFactory(List.of(v1Decoder, v2Decoder));

    @Test
    void selectsTheDecoderThatSupportsTheRequestedFormat() {
        assertThat(factory.forFormat(V1)).isSameAs(v1Decoder);
        assertThat(factory.forFormat(V2)).isSameAs(v2Decoder);
    }

    @Test
    void unknownWireFormat_throwsRatherThanSilentlyPickingOne() {
        // A fleet with mixed firmware versions must never have an old device's
        // bytes silently misinterpreted by the wrong decoder.
        assertThatThrownBy(() -> factory.forFormat(UNKNOWN))
                .isInstanceOf(UnsupportedWireFormat.class);
    }

    /** A minimal decoder stub — just enough to prove factory selection, not decode logic. */
    private static final class FakeDecoder implements FrameDecoder {
        private final WireFormat format;

        FakeDecoder(WireFormat format) {
            this.format = format;
        }

        @Override
        public boolean supports(WireFormat format) {
            return this.format.equals(format);
        }

        @Override
        public List<TelemetryRecord> decode(RawBatch batch) {
            return List.of();
        }
    }
}
