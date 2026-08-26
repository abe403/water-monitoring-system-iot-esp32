# Claims-to-evidence mapping

Every claim about this project should trace to a row here. If it doesn't
have a row, it doesn't go in a résumé, README, or commit message. This file
is updated in the same commit as whatever changes what it can honestly say —
see `docs/DECISIONS/0000-security-remediation.md` for why that discipline
exists.

| Claim | Evidence today | Status |
|---|---|---|
| ESP32-C3 firmware measures tank level and battery temperature | `firmware/water-level.yaml` validates against real ESPHome 2026.8.1 (`esphome config`) | **Schema-valid.** Not yet reflashed to real hardware since the rewrite — see the "known limitation" note at the top of the file. |
| The original filter chain never actually produced a reading | Root-caused via static analysis + corroborated by the HA recorder DB (only 2 injected states, ever) | **Diagnosed, not yet field-confirmed.** Confirm with `esphome logs` across 3+ wake cycles once reflashed. |
| Calibration is versioned and cannot produce an out-of-range level | `LinearTwoPointCalibration` + `LevelPercent`'s constructor invariant; `LinearTwoPointCalibrationTest` covers the original bug, while `TopologyConfigTest` proves the raw Kafka event is calibrated and serialized by the real topology. A live Podman run produced the enriched topic and persisted calibrated readings. | **True and tested**, including a live local Kafka Streams run. |
| Pump has a maximum-runtime safety cutoff and dry-run protection | `firmware/pump-controller.yaml`, schema-validated | **Schema-valid, unverified on hardware.** Dry-run guard fails closed until `stream-processor` publishes the level topic — see `docs/DECISIONS/0006`. |
| An on-device store-and-forward buffer survives deep sleep and outages | `firmware/components/telemetry_outbox/` (RTC ring buffer, POD struct, `static_assert`'d 20-byte record) | **Written, schema-valid config, compiles up through C++ codegen** (full native compile blocked in this environment by a component-registry network restriction, not a code issue — see below). **Not yet flashed or power-cycle tested.** |
| MQTT -> Kafka gateway durably publishes before acknowledging the device | `MqttTelemetryInboundAdapter` uses Paho manual acknowledgements and an ordered worker: Kafka publish confirmation → MQTT application ACK → inbound MQTT PUBACK. `KafkaTelemetryPublisherAdapter` uses a byte-array serializer, `acks=all`, and idempotence. The RF=3 conservation run reported 4,000 observed/unique records and complete application ACKs while a broker was replaced. | **Implemented, unit-tested, and live-tested with replicated Kafka.** |
| Zero data loss under sustained ingestion spikes | `tools/chaos/` ran on Podman-backed kind with 3 Kafka brokers, RF=3, `min.insync.replicas=2`, sustained MQTT input through the real gateway, and deletion of `kafka-1` while the runner was active. `docs/evidence/zero-loss-conservation-2026-08-25.md` records distinct old/replacement pod UIDs, 4,000 expected/observed/unique keys, complete application ACKs, and empty missing, unexpected, duplicate, malformed, publish-error, unavailable-partition, and under-replicated-partition results. | **Demonstrated for this 4,000-record broker-failure scenario.** This is bounded evidence, not a universal guarantee for every load or failure mode. |
| LSTM anomaly detection at 95% accuracy | *(none yet — no LSTM exists)* | **Not demonstrated.** What exists: a Template Method training-pipeline skeleton (`telemetry_ml.pipelines.base`) with 14 passing tests, and one working baseline (`ZScoreBaselinePipeline`) proven end-to-end on synthetic data only. See the synthetic-data caveat below. |
| Baselines a future model must beat | `ThresholdRuleDetector`, `RuleBasedDrainRateDetector`, `RollingZScoreDetector`, `EnsembleDetector` — all implemented and unit-tested | **True.** No LSTM exists yet to compare against them. |
| Evaluation methodology prevents leakage | `split_temporal` with a mandatory embargo, called exactly once inside the non-overridable `TrainingPipeline.run` skeleton; `test_splitting.py` (3 tests) verifies disjointness and embargo behavior | **True and tested.** |
| Event-driven pipeline decouples device input from downstream processing | Full pipeline implemented: MQTT→Kafka ingest; Streams enrichment and gap emission; idempotent Timescale persistence; alert hysteresis; REST; and Kafka→STOMP live telemetry. Under Podman, all seven Flyway migrations applied, a 40-record run became 40 unique rows with zero gaps, the device API returned both devices, and a Kafka anomaly was opened then acknowledged/resolved through the API. | **Implemented, covered by 46 Java tests, and exercised end to end on the local stack.** |
| The operator can monitor the platform without Home Assistant | `console/` is a React 19/TypeScript application with fleet, device, telemetry, gap, and alert routes. It consumes the real REST endpoints, subscribes to `/topic/telemetry` through STOMP/SockJS, supports acknowledge/resolve actions, ships behind same-origin Nginx, and has 7 component/API tests. `docs/evidence/react-operator-console-2026-08-25.md` records the final-image route smoke, an actual simulator reading received by the open browser, and an OPEN → ACKNOWLEDGED → RESOLVED lifecycle completed through the React UI. | **Implemented and live-tested on the local stack.** Pump actuation is intentionally absent until the backend exposes an authenticated, interlocked command API. |
| A device-generated wire frame and the server's decoder agree byte-for-byte | `WireFormatCrossCheckTest.goldenVector_matchesSimulatorEncodedBytes` decodes the simulator's fixed 20-byte little-endian golden vector with the Java decoder; the simulator also has 8 wire/generator tests. | **True and tested across the Python/Java boundary.** Firmware uses the same `static_assert`-guarded 20-byte POD layout, but still needs a real-device capture after flashing. |
| The Java platform builds cleanly with real OOD (hexagonal domain, ArchUnit-enforced) | `platform/domain` includes `NoFrameworkDependenciesTest`; adapter/topology suites cover ingest, stream processing, sink mapping, alert hysteresis, and live WebSocket publication. Full multi-module build succeeds across all 7 modules with 46 tests. | **True and verified in this session.** |
| The Python ml package's pipeline actually runs, not just imports | `ml`: 14/14 tests passing, including `test_zscore_baseline_pipeline_runs_and_flags_the_injected_leak`, a full `TrainingPipeline.run()` execution on synthetic data | **True and verified in this session.** |
| Collaboration with IBM | *(no artifact in this repository can support or refute this)* | **Out of scope for this repo.** If real, describe it as work distinct from this codebase. |

## Honest gaps, in order of what to close next

1. **No real hardware validation.** The firmware changes are schema-valid
   against ESPHome's own validator but unverified by an actual flash-and-run.
2. **The C++ compile got further than expected but did not finish.** It
   passed YAML validation, C++ source generation, and compiler
   identification, then failed at ESP-IDF's CMake step trying to reach
   `components-file.espressif.com` — a network-egress restriction in this
   sandboxed environment, not a defect found in the code up to that point.
   Retry in an environment with unrestricted network access before trusting
   the firmware compiles clean.
3. **No LSTM exists.** The claim in the original résumé paragraph remains
   unsupported. The path to supporting it honestly is unchanged from the
   plan: real data collection (6+ weeks), physically-induced fault labeling,
   baselines before the model, temporal evaluation, and an event-balanced
   held-out set if a headline accuracy number is going to be reported at all.
4. **Native Podman/WSL port publication is unhealthy on this Windows machine.**
   `tools/podman-port-forward.ps1` now supplies a verified, non-admin SSH
   fallback: operations API HTTP, MQTT CONNACK, Kafka ApiVersions, and an
   authenticated TimescaleDB `SELECT 1` all pass. The underlying VirtioProxy
   defect remains external to the application; fallback ports are reported
   when its stale listeners occupy 1883 or 9092.
