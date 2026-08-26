package com.watermonitor.streamprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Detects holes in each device's sequence within a boot epoch. Kafka keeps
 * records for a device key ordered within one partition, so a forward jump
 * is a real missing range; duplicates and a changed boot id are not gaps.
 */
public final class GapDetectionTransformer
        implements ValueTransformerWithKey<String, byte[], byte[]> {

    public static final String STATE_STORE_NAME = "device-contiguity-state";

    private final ObjectMapper json;
    private KeyValueStore<String, String> store;

    public GapDetectionTransformer(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void init(ProcessorContext context) {
        this.store = context.getStateStore(STATE_STORE_NAME);
    }

    @Override
    public byte[] transform(String deviceId, byte[] enrichedValue) {
        try {
            JsonNode node = json.readTree(enrichedValue);
            long bootId = node.get("bootId").asLong();
            long seq = node.get("seq").asLong();
            String encoded = store.get(deviceId);

            if (encoded == null) {
                store.put(deviceId, encode(bootId, seq));
                return null;
            }

            ContiguityState state = decode(encoded);
            if (state.bootId() != bootId) {
                store.put(deviceId, encode(bootId, seq));
                return null;
            }

            if (seq <= state.highestSeq()) {
                return null;
            }

            store.put(deviceId, encode(bootId, seq));
            if (seq == state.highestSeq() + 1) {
                return null;
            }

            return json.writeValueAsBytes(new GapEvent(
                    deviceId,
                    bootId,
                    state.highestSeq() + 1,
                    seq - 1,
                    seq - state.highestSeq() - 1));
        } catch (Exception e) {
            throw new IllegalStateException("gap detection failed for device " + deviceId, e);
        }
    }

    @Override
    public void close() {
    }

    private static String encode(long bootId, long highestSeq) {
        return bootId + ":" + highestSeq;
    }

    private static ContiguityState decode(String value) {
        int separator = value.indexOf(':');
        return new ContiguityState(
                Long.parseLong(value.substring(0, separator)),
                Long.parseLong(value.substring(separator + 1)));
    }

    private record ContiguityState(long bootId, long highestSeq) {
    }

    public record GapEvent(
            String deviceId,
            long bootId,
            long missingSeqFrom,
            long missingSeqTo,
            long gapSize) {
    }
}
