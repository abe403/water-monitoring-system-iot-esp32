# 0000: Security remediation and provenance correction

## Context

An audit of this repository on 2026-08-23 found: live WiFi/API/OTA/AP
passwords committed in plaintext in both ESPHome configs; a long-lived Home
Assistant access token committed in `automation/real_iot_validation.py`; a
committed Home Assistant `.storage/auth` directory containing password hashes
and refresh tokens; and a public repository where every one of those secrets
had been live since the commit that introduced it. Separately, prior
documentation describing this project (an earlier README, résumé bullets
outside this repo) implied more original authorship of the hardware and
firmware than the commit history supports — see `ATTRIBUTION.md`.

## Decision

1. Treat every credential above as compromised. `firmware/secrets.yaml` now
   holds them with an explicit rotation checklist; they must be regenerated
   and the devices re-flashed over USB (not OTA, which trusts the old
   password) before this configuration is trusted in the field.
2. Move all secrets to `!secret` references (`firmware/secrets.yaml`,
   gitignored; `firmware/secrets.yaml.example` committed as a template).
3. Untrack `automation/config/` (the Home Assistant runtime directory) going
   forward; it remains on disk locally but is gitignored.
4. Retire `automation/real_iot_validation.py`, `iot_dashboard_mock.html`,
   `test_iot_dashboard.py`, and `power_bi_integration.py` — each is
   superseded by real components elsewhere in the rewrite (see
   `docs/ARCHITECTURE.md`) and each contained either a hardcoded secret or a
   claim the code didn't back up.
5. Add `ATTRIBUTION.md` crediting the actual authors of the hardware and
   original firmware.

## Consequences

- The working tree is clean of plaintext secrets as of this commit, but **git
  history is not yet rewritten** — the old secrets are still recoverable from
  earlier commits in this public repository. A `git filter-repo` history purge
  and force-push is planned but requires explicit confirmation before
  execution (it is a hard-to-reverse operation on a shared remote); it has not
  been run as part of this pass. Until it is, continue to treat every secret
  that was ever committed as permanently compromised regardless of rotation.
- The MQTT credentials introduced by this rewrite (`mqtt_username`,
  `mqtt_password`) are new and were never committed; they still need real
  values once `infra/` (plan M3) exists.
