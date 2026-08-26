package com.watermonitor.sink.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IngestGapKafkaConsumerTest {

    @Test
    void mapsMissingRangeToGapRepository() {
        IngestGapJdbcRepository repository = mock(IngestGapJdbcRepository.class);
        IngestGapKafkaConsumer consumer = new IngestGapKafkaConsumer(repository, new ObjectMapper());
        byte[] payload = """
                {"deviceId":"tank-01","bootId":7,
                 "missingSeqFrom":11,"missingSeqTo":12,"gapSize":2}
                """.getBytes(StandardCharsets.UTF_8);

        consumer.consume(new ConsumerRecord<>("ingest.gap.v1", 0, 10, "tank-01", payload));

        verify(repository).insert("tank-01", 7, 11, 12, 2);
    }
}
