# 0004: Device liveness is derived server-side, not via MQTT LWT

## Context

MQTT's Last Will and Testament (LWT) mechanism reports a client "offline" as
soon as its TCP connection drops. This device sleeps for 2 out of every 2.5
minutes by design — an LWT-based liveness signal would report it offline on
every single sleep cycle, which is not a fault and would make a genuine
`DeviceOffline` alert indistinguishable from normal operation.

## Decision

No `will_message` is configured. Liveness is derived in
`platform/stream-processor` from missed expected cadence: three consecutive
missed wake cycles (based on the device's own reported sampling interval)
before a device is considered offline.

## Consequences

- Detecting an offline device takes up to ~3 cycles (~7.5 minutes at the
  nominal duty cycle) longer than an LWT would, in exchange for not paging
  anyone on a false positive every 2.5 minutes.
- This logic lives in one place (stream-processor) shared by every consumer,
  rather than being re-derived by each dashboard or alert rule.
