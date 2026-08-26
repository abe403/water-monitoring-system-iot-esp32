# 0006: Pump dry-run protection fails closed when its data dependency is missing

## Context

The pump-controller node has no level sensor of its own — the physical
sensor lives on the separate water-tank node. Dry-run protection (refusing to
run the pump against an empty or unmeasured tank) therefore requires data
from another device, delivered over MQTT as a retained topic
(`wtm/v1/water-tank-01/level/pct`) published by `platform/stream-processor`.
That service does not exist yet as of this firmware change (it is plan
milestone M6); the topic will not be published until it does.

## Decision

Build the consuming side of this guard now, in `firmware/pump-controller.yaml`,
even though the publishing side does not exist yet. The guard checks three
things before allowing the pump to turn on: a level reading has ever been
received, it was received within the last 15 minutes, and it is above a 5%
floor. If any of those is false — including "no reading has ever arrived,
because nothing publishes the topic yet" — the pump refuses to start.

## Consequences

- Today, with no publisher, the guard is permanently active: the pump simply
  cannot be turned on remotely at all. This is the correct default for a
  relay that can run a real pump dry — an unimplemented safety dependency
  should block the dangerous action, not silently no-op past it.
- No firmware change is needed when `platform/stream-processor` starts
  publishing the topic (plan M6); the guard begins functioning as designed
  the moment real data arrives.
- The runtime watchdog (10-minute maximum continuous run, see
  `pump-controller.yaml`) is independent of this guard and active regardless
  — it does not depend on the level topic and protects against a stuck-on
  relay even after the tank data path exists.
