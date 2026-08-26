# 0001: MQTT replaces the Home Assistant native API as the telemetry transport

## Context

The original firmware pushed readings to Home Assistant over ESPHome's native
API. That protocol is a stateful, point-to-point push with no offline
buffering, no acknowledgement the application layer can observe, and no
existence independent of Home Assistant being up. It cannot support the
project's durability goal ("zero data loss under sustained ingestion spikes")
because there is nowhere in that path to attach a delivery guarantee.

## Decision

Replace the native API with MQTT (QoS 1) as the telemetry transport, feeding
`platform/ingest-gateway` rather than Home Assistant directly. Home Assistant
becomes an optional local consumer (via MQTT discovery) rather than the
ingestion path. The device's own store-and-forward buffer
(`firmware/components/telemetry_outbox/`) and an application-level
acknowledgement from the gateway — not MQTT QoS alone — are what actually
back the durability claim; see `docs/ARCHITECTURE.md`, "the durability chain".

## Consequences

- `api:` (native API) is kept only for local diagnostics/logs, not telemetry.
- The device now depends on an MQTT broker (`infra/`, plan M3) being reachable
  to deliver data at all; until that exists, the outbox buffers indefinitely
  and nothing is lost, but nothing is delivered either.
- `automations.yaml`-style HA automations are no longer the place to build
  tank-level logic; that logic now lives server-side in
  `platform/stream-processor` and `platform/alert-service`, where it is
  testable independent of a running HA instance.
