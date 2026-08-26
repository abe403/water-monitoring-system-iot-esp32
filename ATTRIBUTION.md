# Attribution

This project builds on work by multiple contributors. Crediting it accurately
here is a correctness requirement, not a courtesy: several early drafts of
this project's own documentation (résumé bullets, an earlier README) implied
more original authorship than the commit history supports. See
`docs/DECISIONS/0000-security-remediation.md`.

## Hardware and original firmware — FerranST

The KiCad PCB design (`hardware/`) and the original ESPHome configuration and
sensor drivers (`firmware/water-level.yaml`, `firmware/pump-controller.yaml`,
and the initial version of `firmware/components/jsn_sr04t/`) were designed and
committed by **FerranST** (commits `9ed6152`, `80bbe16`, `1b1d7c6`, `bca71be`,
`0b7ac8b`, Oct-Nov 2025). This is the technical foundation the rest of the
project is built on.

## `jsn_sr04t` ESPHome component — @Mafus1

`firmware/components/jsn_sr04t/` originates from an external ESPHome
component authored by **@Mafus1** (`CODEOWNERS = ["@Mafus1"]` in the
component's `sensor.py`). This project has since modified it (extracted
`parse_frame` as a host-testable pure function, added a checksum-error
diagnostic sensor) but the UART framing and protocol handling are @Mafus1's
original work.

## Everything else

Code outside `hardware/` and the original portions of `firmware/` — the
`platform/` (Java/Spring Boot), `ml/` and `simulator/` (Python),
`console/` (React), `infra/`, `contracts/`, and `docs/` directories, plus the
firmware fixes and additions described in `docs/ARCHITECTURE.md` — was written
for this rewrite. Where a specific fix or addition builds directly on
FerranST's or @Mafus1's original code (e.g. the `telemetry_outbox` component
follows the same ESPHome external-component pattern `jsn_sr04t` established),
this is noted in that file's own comments.
