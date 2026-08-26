# React operator console verification — 2026-08-25

## Scope

This evidence covers `console/` as the Home Assistant replacement for routine
monitoring and supported operator actions. It verifies the production bundle,
same-origin REST and STOMP/SockJS integration, responsive rendered output,
alert lifecycle actions, and the Podman deployment.

## Automated gates

- `console`: ESLint passed, TypeScript/Vite production build passed, and 7/7
  Vitest tests passed across API normalization, the tank gauge, and the alert
  ledger.
- `platform`: a forced Gradle test run passed all 46 Java tests, including the
  Kafka-to-STOMP publisher and the operations API.
- Repository-wide supporting checks passed: 14 ML tests, 8 simulator tests,
  and 3 conservation-oracle tests (78 automated tests total including the
  console).
- `git diff --check`, Compose rendering, Nginx configuration validation, and
  `.impeccable/design.json` parsing passed.

## Final deployed image

- Image: `localhost/wtm/operator-console:dev`
- Container health: `healthy`
- Console tunnel: `127.0.0.1:18080 -> console:8080`, HTTP 200
- The Windows Podman forwarding verifier also passed the operations API,
  MQTT CONNACK, Kafka ApiVersions, and authenticated TimescaleDB `SELECT 1`
  probes.
- The SPA response returned `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, a restrictive Permissions Policy, Referrer Policy,
  and a Content Security Policy limited to same-origin application resources
  plus same-origin WebSocket connections. Hashed assets receive a one-year
  cache lifetime; the HTML shell is `no-cache`.

## Browser route smoke

A controlled Chrome session loaded the final CSP-protected deployment without
runtime or console errors:

| Route | Expected heading | Result |
|---|---|---|
| `/` | Operations overview | PASS |
| `/devices` | Device fleet | PASS |
| `/devices/chaos-001` | chaos-001 | PASS |
| `/alerts` | Alert workspace | PASS |
| `/does-not-exist` | Station not found | PASS |

The console proxy returned HTTP 200 for devices, active alerts, persisted
telemetry, and `/ws/info`.

## Live telemetry proof

With the final deployed console open and reporting `Connected live`, the
simulator published one binary reading for `chaos-001` through the forwarded
MQTT broker. The browser then reported:

- a non-empty `Last live event` timestamp;
- delivery-chain LIVE stage `Confirmed`;
- the new committed sequence in the overview; and
- no browser runtime or CSP errors.

The settled live capture is `.impeccable/review/live-event.png`.

## Operator-action proof

A `LEAK` anomaly score was published to `anomaly.scores.v1`. In a controlled
Chrome session, the React alert workspace rendered the resulting OPEN alert,
the operator selected **Acknowledge**, the row changed to ACKNOWLEDGED, then
selected **Resolve** and confirmed the destructive action. The active API
returned zero alerts and the UI rendered its explicit clear state. Captures:

- `.impeccable/review/alert-open.png`
- `.impeccable/review/alert-acknowledged.png`
- `.impeccable/review/alert-resolved.png`

## Visual and accessibility review

Final desktop (`1440x1100`) and mobile (`390x844`) captures live at
`.impeccable/review/desktop.png` and `mobile.png`. An independent finish
review returned PASS after verifying responsive hierarchy, loading/error/empty
truthfulness, selected-device live evidence, keyboard-complete drawer
semantics, continuous mobile delivery-chain navigation, chart text
equivalence, and tank-label contrast.

## Intentional safety boundary

The console does not expose pump actuation. The current backend has no
authenticated, interlocked pump-command API with delivery confirmation, so a
control in this frontend would falsely imply a safety guarantee. OAuth2/JWT is
enabled by default in the production API; local Compose explicitly disables
it, and the console transport accepts a runtime bearer token without inventing
an identity-provider flow.
