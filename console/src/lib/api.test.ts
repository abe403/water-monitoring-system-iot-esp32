import { describe, expect, it } from "vitest";
import { normalizeAlert, normalizeDevice, normalizeReading } from "./api";

describe("API normalization", () => {
  it("normalizes JDBC snake-case device rows", () => {
    expect(normalizeDevice({
      device_id: "tank-01",
      provisioned: true,
      first_seen_at: "2026-08-25T00:00:00Z",
      last_seen_at: "2026-08-25T01:00:00Z",
      total_readings: 42,
      active_alerts: 1,
    })).toMatchObject({ deviceId: "tank-01", totalReadings: 42, activeAlerts: 1 });
  });

  it("normalizes persisted and live reading field names to one contract", () => {
    const persisted = normalizeReading({
      device_id: "tank-01", boot_id: 3, seq: 8,
      observed_at: "2026-08-25T00:00:00Z", received_at: "2026-08-25T00:00:01Z",
      distance_mm: 350, temp_tenths: 241, rssi_dbm: -63, level_pct: 67.4, quality: "GOOD",
    });
    const live = normalizeReading({
      deviceId: "tank-01", bootId: 3, seq: 8,
      observedAt: "2026-08-25T00:00:00Z", receivedAt: "2026-08-25T00:00:01Z",
      distanceMm: 350, tempTenthsCelsius: 241, rssiDbm: -63, levelPct: 67.4, quality: "GOOD",
    });
    expect(live).toEqual(persisted);
  });

  it("uses a device fallback for device-scoped alert history", () => {
    expect(normalizeAlert({ id: "a1", anomaly_type: "LEAK", state: "OPEN" }, "tank-01"))
      .toMatchObject({ id: "a1", deviceId: "tank-01", anomalyType: "LEAK", state: "OPEN" });
  });
});
