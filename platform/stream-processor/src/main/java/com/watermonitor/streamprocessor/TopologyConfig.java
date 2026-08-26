package com.watermonitor.streamprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@Configuration
@EnableKafkaStreams
public class TopologyConfig {

    static final String INPUT_TOPIC = "telemetry.raw.v1";
    static final String OUTPUT_TOPIC = "telemetry.enriched.v1";
    static final String GAP_TOPIC = "ingest.gap.v1";

    @Bean
    public org.apache.kafka.streams.kstream.KStream<String, byte[]> enrichmentStream(
            StreamsBuilder builder, ObjectMapper json) {
        StoreBuilder<KeyValueStore<String, Long>> lastSeenStore =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("device-last-seen"),
                        Serdes.String(),
                        Serdes.Long());
        builder.addStateStore(lastSeenStore);

        StoreBuilder<KeyValueStore<String, String>> contiguityStore =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(GapDetectionTransformer.STATE_STORE_NAME),
                        Serdes.String(),
                        Serdes.String());
        builder.addStateStore(contiguityStore);

        var stream = builder.stream(INPUT_TOPIC,
                Consumed.with(Serdes.String(), Serdes.ByteArray()));

        var enriched = stream.transformValues(
                () -> new TelemetryEnrichmentTransformer(json), "device-last-seen");

        enriched.to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));

        enriched.transformValues(
                        () -> new GapDetectionTransformer(json),
                        GapDetectionTransformer.STATE_STORE_NAME)
                .filter((deviceId, gap) -> gap != null)
                .to(GAP_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));

        return enriched;
    }
}
