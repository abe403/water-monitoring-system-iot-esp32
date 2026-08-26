# 0003: Keep the Arduino framework rather than migrate to esp-idf

## Context

esp-idf offers lower deep-sleep current draw than ESPHome's Arduino framework
and is the more common choice for battery/solar ESP32 projects for that
reason. Switching frameworks changes UART, ADC, and RTC-memory timing and
initialization order in ways that are hard to predict from documentation
alone and that this rewrite has no way to validate — there is no CI runner or
available bench unit with the physical board attached.

## Decision

Keep `framework: type: arduino`. The power-budget arithmetic in
`docs/ARCHITECTURE.md` already accounts for the Arduino framework's current
draw and shows the existing solar/battery budget is viable at the nominal
30s/2min duty cycle; the framework switch is a possible future optimization,
not a correctness requirement.

## Consequences

- `firmware/components/telemetry_outbox` uses `RTC_NOINIT_ATTR`, which is
  available and behaves the same way under both frameworks (both compile
  through the same underlying esp-idf toolchain; ESPHome's "Arduino
  framework" is Arduino-as-an-esp-idf-component, not a different linker).
  Nothing about the outbox design depends on this decision.
- Revisit once a bench unit is available (plan M7) to measure real current
  draw and decide whether the esp-idf migration is worth the retest cost.
