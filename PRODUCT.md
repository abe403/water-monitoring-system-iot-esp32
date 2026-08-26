# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

ReactJS application. TypeScript, build tooling, state/query libraries, charting, and deployment details are delegated to this implementation, with the existing Spring REST and STOMP/SockJS contracts remaining authoritative.

## Users

Primary user (inferred pending confirmation): a technical household or site operator responsible for understanding tank status, telemetry health, and active anomalies from desktop or mobile.

## Product Purpose

Replace Home Assistant as the daily operator interface for the water-monitoring platform. Success means an operator can see fleet health, inspect current and historical readings, identify telemetry gaps, receive live updates, and acknowledge or resolve alerts without leaving this product.

## Positioning

The console exposes the platform's end-to-end telemetry evidence directly: device recency, calibrated tank level, sensor quality, sequence gaps, anomaly lifecycle, and live Kafka-backed updates are presented as one operational record rather than as generic smart-home entities.

## Operating Context

- Used for routine monitoring and incident response on desktop and mobile browsers.
- Reads the existing operations API under `/api/v1` and live STOMP/SockJS telemetry under `/ws`.
- Local development runs beside the Podman Compose stack; production should serve the frontend and proxy API/WebSocket traffic through one origin.
- Operators need the last trustworthy state to remain legible when live connectivity is degraded.

## Capabilities and Constraints

- Device fleet list and detail, telemetry history and hourly aggregates, ingestion gaps, active/device alert history, alert acknowledgement, and manual resolution are backed by existing endpoints.
- Live telemetry is available on `/topic/telemetry` and `/topic/telemetry/{deviceId}`.
- Pump operation is not exposed until a dedicated, authenticated, fail-safe backend command API exists. The UI must not simulate or imply command delivery.
- Production API authentication is OAuth2/JWT-enabled by default; local Compose disables it. The first console release will keep its API transport ready for bearer tokens without inventing an identity provider flow.
- All operational states need explicit loading, empty, stale, offline, and failure treatments.

## Evidence on Hand

- REST controllers and WebSocket configuration: `platform/operations-api/src/main/java/com/watermonitor/api/`
- Persistence schema: `platform/timescale-sink/src/main/resources/db/migration/`
- Architecture and claim status: `docs/ARCHITECTURE.md`, `docs/claims.md`
- Live telemetry publisher tests: `platform/operations-api/src/test/java/com/watermonitor/api/live/LiveTelemetryPublisherTest.java`
- No approved logo, brand system, customer testimonials, production screenshots, or pump-command API exist; future work must not fabricate them.

## Product Principles

- Lead with operational truth: recency, quality, and provenance accompany every headline value.
- Make abnormal conditions visible without making healthy operation noisy.
- Preserve operator context through reconnects, refreshes, empty datasets, and partial failures.
- Never present an unsafe or unsupported action as available.
- Use the same interface comfortably at a workstation and beside the physical equipment on a phone.

## Accessibility & Inclusion

Target WCAG 2.2 AA: keyboard-complete navigation, visible focus, semantic status announcements, non-color-only state communication, reduced-motion support, and readable charts with textual summaries.
