package com.watermonitor.streamprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Domain-fact enrichment: applies the versioned {@code CalibrationStrategy}
 * effective at each reading's timestamp, assigns {@code Quality} flags,
 * tracks per-device {@code (bootId, seq)} contiguity, and runs the physics-
 * based rule detectors. Deliberately computes no model features — see
 * docs/ARCHITECTURE.md, "the training/serving skew boundary": every model
 * feature is owned by {@code ml/}, imported by both training and inference,
 * so this service and the Python training pipeline can never disagree about
 * what a feature means.
 *
 * <p>The topology itself (see the planned {@code EnrichmentTopology} class)
 * should stay a plain {@code Topology buildTopology(StreamsBuilder, config)}
 * function, not a class hierarchy — Kafka Streams' DSL is already the right
 * abstraction for a dataflow graph; wrapping it in objects would obscure the
 * one thing a reader needs to see quickly. Domain objects
 * ({@code CalibrationStrategy}, rule detectors) are injected into the
 * topology, not the other way around.
 */
@SpringBootApplication
public class StreamProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamProcessorApplication.class, args);
    }
}
