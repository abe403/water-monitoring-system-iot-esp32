package com.watermonitor.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The MQTT-to-Kafka durability gateway. Deployed as its own process
 * (separate from the {@code platform} module bundle) because it scales on a
 * different axis than the rest of the system: MQTT connection/session count,
 * not request throughput. See docs/ARCHITECTURE.md, "Java: ports and
 * adapters" for why this module owns nothing except decoding, validating,
 * publishing, and acknowledging — no business rules live here.
 */
@SpringBootApplication
public class IngestGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestGatewayApplication.class, args);
    }
}
