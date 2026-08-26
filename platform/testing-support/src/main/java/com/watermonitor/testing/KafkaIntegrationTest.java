package com.watermonitor.testing;

import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real (single-broker) Kafka.
 *
 * <p>Deliberately NOT where the project's durability claim gets validated —
 * a single broker cannot satisfy {@code min.insync.replicas=2}, so a "green"
 * test against this container proves nothing about the zero-loss guarantee.
 * That validation runs on a 3-broker cluster in CI (plan milestone M4, "the
 * conservation test"), not here. This base class exists for ordinary
 * functional integration tests: does the gateway decode and forward
 * correctly, does the sink upsert idempotently, and so on.
 */
@Testcontainers
public abstract class KafkaIntegrationTest {

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.0"));

    static {
        KAFKA.start();
    }
}
