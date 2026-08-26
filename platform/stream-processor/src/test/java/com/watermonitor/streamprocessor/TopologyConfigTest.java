package com.watermonitor.streamprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TopologyConfigTest {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> input;
    private TestOutputTopic<String, byte[]> enriched;
    private TestOutputTopic<String, byte[]> gaps;

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        new TopologyConfig().enrichmentStream(builder, json);

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "enrichment-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");

        driver = new TopologyTestDriver(builder.build(), properties);
        input = driver.createInputTopic(
                TopologyConfig.INPUT_TOPIC,
                Serdes.String().serializer(),
                Serdes.ByteArray().serializer());
        enriched = driver.createOutputTopic(
                TopologyConfig.OUTPUT_TOPIC,
                Serdes.String().deserializer(),
                Serdes.ByteArray().deserializer());
        gaps = driver.createOutputTopic(
                TopologyConfig.GAP_TOPIC,
                Serdes.String().deserializer(),
                Serdes.ByteArray().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void enrichesRawEventWithCalibrationAndIsoTimestamps() throws Exception {
        input.pipeInput("tank-01", raw("tank-01", 1, 0, 1_724_400_000_000L, 0.460, 24.5, -62));

        JsonNode value = json.readTree(enriched.readValue());
        assertThat(value.get("deviceId").asText()).isEqualTo("tank-01");
        assertThat(value.get("distanceMm").asInt()).isEqualTo(460);
        assertThat(value.get("tempTenthsCelsius").asInt()).isEqualTo(245);
        assertThat(value.get("levelPct").asDouble()).isCloseTo(50.0, within(1e-9));
        assertThat(value.get("quality").asText()).isEqualTo("GOOD");
        assertThat(value.get("observedAt").asText()).isEqualTo("2024-08-23T08:00:00Z");
        assertThat(value.get("interarrivalMs").asLong()).isZero();
        assertThat(gaps.isEmpty()).isTrue();
    }

    @Test
    void tracksInterarrivalPerDevice() throws Exception {
        input.pipeInput("tank-01", raw("tank-01", 1, 0, 1_000, 0.4, 22.0, -55));
        input.pipeInput("tank-01", raw("tank-01", 1, 1, 3_500, 0.4, 22.0, -55));

        enriched.readValue();
        JsonNode second = json.readTree(enriched.readValue());
        assertThat(second.get("interarrivalMs").asLong()).isEqualTo(2_500);
    }

    @Test
    void emitsExactMissingRangeForSequenceHole() throws Exception {
        input.pipeInput("tank-01", raw("tank-01", 7, 10, 1_000, 0.4, 22.0, -55));
        input.pipeInput("tank-01", raw("tank-01", 7, 13, 2_000, 0.4, 22.0, -55));

        JsonNode gap = json.readTree(gaps.readValue());
        assertThat(gap.get("deviceId").asText()).isEqualTo("tank-01");
        assertThat(gap.get("bootId").asLong()).isEqualTo(7);
        assertThat(gap.get("missingSeqFrom").asLong()).isEqualTo(11);
        assertThat(gap.get("missingSeqTo").asLong()).isEqualTo(12);
        assertThat(gap.get("gapSize").asLong()).isEqualTo(2);
        assertThat(gaps.isEmpty()).isTrue();
    }

    @Test
    void duplicateDoesNotEmitAnotherGap() {
        input.pipeInput("tank-01", raw("tank-01", 7, 10, 1_000, 0.4, 22.0, -55));
        input.pipeInput("tank-01", raw("tank-01", 7, 10, 2_000, 0.4, 22.0, -55));

        assertThat(gaps.isEmpty()).isTrue();
    }

    @Test
    void newBootEpochResetsContiguity() {
        input.pipeInput("tank-01", raw("tank-01", 7, 10, 1_000, 0.4, 22.0, -55));
        input.pipeInput("tank-01", raw("tank-01", 8, 99, 2_000, 0.4, 22.0, -55));

        assertThat(gaps.isEmpty()).isTrue();
    }

    private static byte[] raw(
            String deviceId,
            long bootId,
            long seq,
            long observedAtEpochMs,
            double distanceM,
            double temperatureC,
            int rssiDbm) {
        String value = """
                {"deviceId":"%s","bootId":%d,"seq":%d,
                 "observedAtEpochMs":%d,"ingestedAtEpochMs":%d,
                 "distanceM":%s,"temperatureC":%s,"rssiDbm":%d,
                 "checksumErrorsAtReceipt":0}
                """.formatted(
                deviceId, bootId, seq, observedAtEpochMs, observedAtEpochMs + 50,
                distanceM, temperatureC, rssiDbm);
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
