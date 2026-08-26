package com.watermonitor.ingest.config;

import com.watermonitor.domain.ingestion.FrameDecoderFactory;
import com.watermonitor.domain.ingestion.IngestTelemetryBatchUseCase;
import com.watermonitor.domain.ports.AckPort;
import com.watermonitor.domain.ports.DeadLetterPort;
import com.watermonitor.domain.ports.DeviceRegistryPort;
import com.watermonitor.domain.ports.TelemetryPublisherPort;
import com.watermonitor.ingest.adapter.TelemetryOutboxV1Decoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class IngestUseCaseConfig {

    @Bean
    public FrameDecoderFactory frameDecoderFactory(TelemetryOutboxV1Decoder v1Decoder) {
        return new FrameDecoderFactory(List.of(v1Decoder));
    }

    @Bean
    public IngestTelemetryBatchUseCase ingestTelemetryBatchUseCase(
            FrameDecoderFactory decoders,
            DeviceRegistryPort registry,
            TelemetryPublisherPort publisher,
            AckPort ack,
            DeadLetterPort deadLetters) {
        return new IngestTelemetryBatchUseCase(decoders, registry, publisher, ack, deadLetters);
    }
}
