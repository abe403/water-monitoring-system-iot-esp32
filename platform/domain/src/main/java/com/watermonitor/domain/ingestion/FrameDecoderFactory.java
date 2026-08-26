package com.watermonitor.domain.ingestion;

import java.util.List;

/**
 * A registry, not a switch statement — the Open/Closed point for firmware
 * wire-format drift. A device fleet upgrades gradually, so the gateway must
 * keep decoding every wire version any still-deployed firmware speaks; a
 * {@code switch} on format version would need editing (and redeploying)
 * every time a new firmware version ships. Adding support for a new version
 * means adding a new {@link FrameDecoder} bean, not touching this class.
 *
 * <p>Contrast with {@code firmware/components/jsn_sr04t}, whose {@code switch
 * (model_)} over exactly two sensor variants is the right call in that spot:
 * a closed, two-element set, no DI container, and code size matters on a
 * microcontroller. The same shape is wrong here because the set of wire
 * formats is open and changes on every firmware release.
 */
public final class FrameDecoderFactory {

    private final List<FrameDecoder> decoders;

    public FrameDecoderFactory(List<FrameDecoder> decoders) {
        this.decoders = List.copyOf(decoders);
    }

    public FrameDecoder forFormat(WireFormat format) {
        return decoders.stream()
                .filter(d -> d.supports(format))
                .findFirst()
                .orElseThrow(() -> new UnsupportedWireFormat(format));
    }
}
