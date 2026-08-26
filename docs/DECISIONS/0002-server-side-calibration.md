# 0002: Smoothing and calibration move server-side

## Context

The original `water-level.yaml` computed a calibrated level percentage
on-device through a filter chain: `median(window_size=8, send_every=4)` ->
`clamp(0.20m, 1.15m)` -> `exponential_moving_average(alpha=0.1, send_every=8)`
-> a linear calibration lambda.

This chain cannot ever publish under the device's own wake schedule. A 30s
wake window at a 3s poll interval yields roughly 10 raw samples; the median
stage emits about 2 outputs from that; the EMA stage requires 8 inputs before
it emits once. ESP32 deep sleep is a full reset, so the EMA's internal counter
never accumulates across wakes — it reaches 2 of the 8 it needs and resets,
forever. The `level` template sensor consequently never received a state.
This was corroborated by the project's Home Assistant recorder database,
whose only two `sensor.water_level` states were both injected directly over
the REST API by a test script, not measured by the device.

Separately, the clamp bounds (up to 1.15m) and the calibration line
(`level = -192.31 * distance + 138.46`, which is 0% at 0.72m) disagreed,
so a valid clamped reading could still produce a level below -80%.

## Decision

The device now publishes raw distance (see `firmware/water-level.yaml`,
`components/jsn_sr04t`) with no on-device smoothing or calibration. Smoothing,
calibration, and clamping to `[0, 100]` move to
`platform/stream-processor`, applied via a `CalibrationStrategy` selected by
the reading's `observedAt` timestamp (see `docs/ARCHITECTURE.md`, "Java:
ports and adapters").

## Consequences

- Server-side calibration can be corrected or re-versioned without reflashing
  hardware, and history can be re-derived after a recalibration by replaying
  raw readings through the new profile.
- It is testable with ordinary unit tests instead of requiring a physical
  device and 8+ wake cycles to observe an output.
- It is immune to the deep-sleep state-reset bug described above by
  construction: there is no cross-wake accumulator on the device to reset.
- The device now sends more data (one message per wake instead of one every
  ~8 wakes), which is the correct tradeoff given `firmware/components/
  telemetry_outbox` exists specifically to make per-message transport cheap
  and durable.
