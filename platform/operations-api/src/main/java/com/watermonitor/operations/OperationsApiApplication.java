package com.watermonitor.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The operator-facing BFF: REST over device history and calibration
 * profiles, alert acknowledge/resolve, and a STOMP-over-WebSocket feed for
 * {@code console/}. This is the literal "operator-facing dashboard" half of
 * the project's telemetry-monitoring claim — see docs/ARCHITECTURE.md.
 */
@SpringBootApplication(scanBasePackages = "com.watermonitor")
public class OperationsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperationsApiApplication.class, args);
    }
}
