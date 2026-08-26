# Water Monitoring System

An event-driven IoT telemetry pipeline from ESP32 sensor nodes through
Apache Kafka to a TimescaleDB-backed operator console, with anomaly
detection via time-series baselines and (planned) LSTM inference.

Built on top of [FerranST's open-source ESP32-C3 water-tank hardware and
firmware](https://github.com/FerranST) — see [ATTRIBUTION.md](ATTRIBUTION.md).

## Architecture

Full design, durability chain analysis, and OOD rationale:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

Claim-to-evidence mapping (what is real vs. scaffolded):
[docs/claims.md](docs/claims.md)

```
ESP32-C3 ──MQTT QoS1──▶ Mosquitto ──▶ ingest-gateway ──▶ Kafka ──▶ stream-processor
                                         (acks=all)        │              │
                                                           ▼              ▼
                                                    timescale-sink   inference
                                                     (idempotent)       │
                                                           │            ▼
                                                    TimescaleDB    alert-service
                                                           │            │
                                                           ▼            ▼
                                                       operations-api ──▶ console
```

## Repository layout

```
firmware/         ESPHome configs + external components (ESP32-C3 edge nodes)
hardware/         KiCad PCB design (rev-3, solar-powered)
console/          React 19 + TypeScript operator console (REST + STOMP/WebSocket)
platform/         Java 25 / Spring Boot 3.5, Gradle multi-module
  domain/           pure hexagonal core — zero framework imports (ArchUnit-enforced)
  ingest-gateway/   MQTT → Kafka durability chain (acks=all, idempotent producer)
  stream-processor/ Kafka Streams: calibration, quality flags, gap detection
  alert-service/    anomaly score consumer, alert lifecycle with hysteresis
  operations-api/   REST + WebSocket (STOMP) for the operator console
  timescale-sink/   idempotent upsert, Flyway migrations, continuous aggregates
  testing-support/  shared test fixtures
ml/               Python: feature engineering, training pipeline, anomaly detectors
simulator/        synthetic telemetry + fault injection, same 20-byte wire format
infra/            Docker Compose (Kafka KRaft, Mosquitto, TimescaleDB, Prometheus, Grafana)
tools/chaos/       3-broker conservation test with broker fault injection
docs/             architecture, ADRs, claims mapping
```

## What works today

- **Firmware**: two ESPHome configs (sensing node + pump controller) validated
  against ESPHome 2026.8.1. The original fatal filter-chain bug (device never
  produced a reading due to EMA starvation under deep sleep) is fixed.
  Pump controller has dry-run protection and runtime cutoff.
- **On-device durability**: `telemetry_outbox` external component — RTC ring
  buffer surviving deep sleep, monotonic sequence numbers, application-level
  end-to-end acknowledgement (stronger than MQTT PUBACK).
- **Java platform**: full 7-module Gradle build passes. Tests cover the
  framework-free domain, MQTT→Kafka durability ordering, Kafka Streams
  enrichment and gap emission, Timescale consumer mapping, alert hysteresis,
  and Kafka→STOMP live telemetry. All adapters are wired.
- **Python ML**: 14 tests passing. Template Method training pipeline, temporal
  split with embargo, z-score/threshold/ensemble baseline detectors, full
  pipeline end-to-end on synthetic data.
- **Simulator**: 8 tests passing. Byte-identical wire encoding to the
  firmware and Java decoder.
- **Durability test**: a kind-based 3-broker Kafka harness publishes through
  MQTT and the real ingest gateway, deletes a broker during sustained load,
  and separately reports missing, unexpected, duplicate, malformed, and
  application-ack results. A recorded Podman-backed run conserved 4,000/4,000
  unique records with complete application acknowledgements while replacing
  a broker at RF=3 and `min.insync.replicas=2`; all partitions returned to
  full ISR with none unavailable or under-replicated. Its pure conservation
  oracle has 3 tests.
- **TimescaleDB schema**: 7 Flyway migrations covering hypertables, device
  registry, calibration profiles, alert table, ingest gap tracking,
  continuous aggregates (1min/1hr), and compression/retention policies.
- **Operator console**: a responsive React 19/TypeScript interface replaces
  Home Assistant for day-to-day monitoring. It provides the fleet overview,
  tank level and telemetry history, data-quality and gap diagnostics, active
  alert acknowledgement/resolution, and live STOMP updates through the same
  origin as the REST API. Seven frontend tests cover data normalization and
  the critical tank and alert surfaces.

Local verification currently covers 46 Java tests, 14 ML tests, 8 simulator
tests, 3 conservation-oracle tests, and 7 console tests: 78 tests total.

## What is not yet demonstrated

See [docs/claims.md](docs/claims.md) for the honest status of every claim.
The complete local pipeline has now run under Podman: a 40-record MQTT load
was conserved into Kafka with complete application acknowledgements, persisted
as 40 unique TimescaleDB rows, and exposed through the operations API. The
alert Kafka consumer and acknowledge/resolve API lifecycle were also exercised.
The replicated failure-injection run also completed successfully with 4,000
expected, observed, and unique records and no loss, duplicates, malformed
records, unexpected records, or publish errors. Real-hardware validation and
the field-data/LSTM campaign remain open.

## Prerequisites

- JDK 25 (tested with Amazon Corretto)
- Python 3.11+
- Podman plus `podman-compose` (default), or Docker Compose
- ESPHome CLI (for firmware flashing)

## Quick start

```bash
# Java platform
cd platform && ./gradlew build

# Python ML
cd ml && pip install -e ".[dev]" && pytest

# Simulator
cd simulator && pip install -e ".[dev]" && pytest

# React operator console (development server)
cd console && npm ci && npm run dev

# Full local stack (Podman default)
python -m pip install podman-compose
make up

# Docker-compatible alternative
make up CONTAINER_ENGINE=docker

# Repeat the local 40-record conservation smoke test
make conservation-local

# Three-broker failure-injection test (requires kind and kubectl)
make conservation
```

The Compose deployment serves the operator console at `http://localhost:8080`.
Nginx keeps REST (`/api/`) and STOMP/SockJS (`/ws/`) on that same origin and
proxies them to `operations-api`; no Home Assistant runtime is required.

On Windows, invoke the harness with Git Bash explicitly so `bash` does not
resolve to the Podman-machine WSL distribution:

```powershell
$env:CONTAINER_ENGINE = "podman"
$env:KIND_PROVIDER = "podman"
& "C:\Program Files\Git\bin\bash.exe" tools/chaos/run-kind.sh
```

### Windows Podman port forwarding

If Podman's Windows VirtioProxy is unhealthy, the published container ports
may appear to be listening locally while refusing connections. The portable
helper [tools/podman-port-forward.ps1](tools/podman-port-forward.ps1) uses the
Podman machine's OpenSSH endpoint and dynamically discovered container IPs,
without requiring Administrator/HNS access. It forwards the React console to
`localhost:8080`, operations API to `localhost:8084`, MQTT to
`localhost:1883`, Kafka to `localhost:9092`, and
TimescaleDB to `localhost:15432` (the host's usual PostgreSQL port, 5432, is
left untouched). When broken VirtioProxy listeners occupy those ports, the
zero-argument workflow automatically falls back to `18080` for the console,
`18084` for the API, `11883` for MQTT, and `19092` for Kafka;
`forward-status` reports the actual persisted mappings.

```powershell
# Start, verify, inspect, and stop the tunnel
make forward-start
make forward-test
make forward-status
make forward-stop
```

The helper stores only a PID and endpoint metadata under
`%LOCALAPPDATA%\water-monitoring-system-iot-esp32`; logs are stored beside it.
`Start` is idempotent and refreshes stale container IPs. It refuses to kill
unrelated processes. Pass `-StrictPorts` to demand the exact default ports
after removing a published mapping; otherwise Docker remains supported by the
normal Compose targets and this helper is a Windows Podman fallback.

## Decisions

Architectural decisions are recorded in `docs/DECISIONS/`:

- [0000](docs/DECISIONS/0000-security-remediation.md) — Security remediation
- [0001](docs/DECISIONS/0001-mqtt-over-native-api.md) — MQTT over HA native API
- [0002](docs/DECISIONS/0002-server-side-calibration.md) — Server-side calibration
- [0003](docs/DECISIONS/0003-keep-arduino-framework.md) — Keep Arduino framework
- [0004](docs/DECISIONS/0004-server-side-liveness.md) — Server-side liveness
- [0005](docs/DECISIONS/0005-pod-structs-in-rtc-memory.md) — POD structs in RTC memory
- [0006](docs/DECISIONS/0006-dry-run-protection.md) — Dry-run protection fails closed

## License

See [LICENSE](LICENSE).
