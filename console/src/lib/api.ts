import type {
  ActionResult,
  Alert,
  Device,
  HourlyReading,
  IngestGap,
  JsonRecord,
  Reading,
} from "./types";

const apiBase = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

const value = (row: JsonRecord, ...keys: string[]): unknown => {
  for (const key of keys) {
    if (row[key] !== undefined) return row[key];
  }
  return undefined;
};

const stringValue = (row: JsonRecord, ...keys: string[]): string =>
  String(value(row, ...keys) ?? "");

const nullableString = (row: JsonRecord, ...keys: string[]): string | null => {
  const candidate = value(row, ...keys);
  return candidate === undefined || candidate === null ? null : String(candidate);
};

const numberValue = (row: JsonRecord, ...keys: string[]): number => {
  const parsed = Number(value(row, ...keys) ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

const nullableNumber = (row: JsonRecord, ...keys: string[]): number | null => {
  const candidate = value(row, ...keys);
  if (candidate === undefined || candidate === null || candidate === "") return null;
  const parsed = Number(candidate);
  return Number.isFinite(parsed) ? parsed : null;
};

function accessToken(): string | null {
  const configured = import.meta.env.VITE_ACCESS_TOKEN as string | undefined;
  if (configured) return configured;
  try {
    return sessionStorage.getItem("waterline.access_token");
  } catch {
    return null;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set("Accept", "application/json");
  const token = accessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${apiBase}${path}`, { ...init, headers });
  if (!response.ok) {
    const detail = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(detail?.message || `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function normalizeDevice(row: JsonRecord): Device {
  return {
    deviceId: stringValue(row, "device_id", "deviceId"),
    hardwareRevision: nullableString(row, "hardware_revision", "hardwareRevision"),
    firmwareVersion: nullableString(row, "firmware_version", "firmwareVersion"),
    provisioned: Boolean(value(row, "provisioned")),
    firstSeenAt: stringValue(row, "first_seen_at", "firstSeenAt"),
    lastSeenAt: stringValue(row, "last_seen_at", "lastSeenAt"),
    totalReadings: numberValue(row, "total_readings", "totalReadings"),
    activeAlerts: numberValue(row, "active_alerts", "activeAlerts"),
  };
}

export function normalizeReading(row: JsonRecord): Reading {
  return {
    deviceId: stringValue(row, "device_id", "deviceId"),
    bootId: numberValue(row, "boot_id", "bootId"),
    seq: numberValue(row, "seq"),
    observedAt: stringValue(row, "observed_at", "observedAt"),
    receivedAt: stringValue(row, "received_at", "receivedAt"),
    distanceMm: nullableNumber(row, "distance_mm", "distanceMm"),
    tempTenths: nullableNumber(row, "temp_tenths", "tempTenthsCelsius", "tempTenths"),
    rssiDbm: nullableNumber(row, "rssi_dbm", "rssiDbm"),
    levelPct: nullableNumber(row, "level_pct", "levelPct"),
    quality: stringValue(row, "quality") || "MISSING",
    interarrivalMs: nullableNumber(row, "interarrivalMs") ?? undefined,
    wireVersion: nullableNumber(row, "wireVersion") ?? undefined,
  };
}

export function normalizeAlert(row: JsonRecord, fallbackDeviceId = ""): Alert {
  return {
    id: stringValue(row, "id"),
    deviceId: stringValue(row, "device_id", "deviceId") || fallbackDeviceId,
    anomalyType: stringValue(row, "anomaly_type", "anomalyType"),
    state: stringValue(row, "state"),
    score: nullableNumber(row, "score"),
    openedAt: stringValue(row, "opened_at", "openedAt"),
    updatedAt: stringValue(row, "updated_at", "updatedAt"),
    resolvedAt: nullableString(row, "resolved_at", "resolvedAt"),
    resolvedBy: nullableString(row, "resolved_by", "resolvedBy"),
  };
}

export const api = {
  base: apiBase,
  listDevices: async (): Promise<Device[]> =>
    (await request<JsonRecord[]>("/api/v1/devices")).map(normalizeDevice),
  getDevice: async (deviceId: string): Promise<Device> =>
    normalizeDevice(await request<JsonRecord>(`/api/v1/devices/${encodeURIComponent(deviceId)}`)),
  getReadings: async (deviceId: string, hours: number): Promise<Reading[]> =>
    (await request<JsonRecord[]>(`/api/v1/telemetry/${encodeURIComponent(deviceId)}?hours=${hours}`))
      .map(normalizeReading),
  getHourly: async (deviceId: string, hours: number): Promise<HourlyReading[]> =>
    (await request<JsonRecord[]>(`/api/v1/telemetry/${encodeURIComponent(deviceId)}/hourly?hours=${hours}`))
      .map((row) => ({
        deviceId: stringValue(row, "device_id", "deviceId"),
        bucket: stringValue(row, "bucket"),
        averageDistanceMm: nullableNumber(row, "avg_distance_mm", "averageDistanceMm"),
        averageTempTenths: nullableNumber(row, "avg_temp_tenths", "averageTempTenths"),
        averageRssiDbm: nullableNumber(row, "avg_rssi_dbm", "averageRssiDbm"),
        averageLevelPct: nullableNumber(row, "avg_level_pct", "averageLevelPct"),
        minimumLevelPct: nullableNumber(row, "min_level_pct", "minimumLevelPct"),
        maximumLevelPct: nullableNumber(row, "max_level_pct", "maximumLevelPct"),
        sampleCount: numberValue(row, "sample_count", "sampleCount"),
      })),
  getGaps: async (deviceId: string): Promise<IngestGap[]> =>
    (await request<JsonRecord[]>(`/api/v1/devices/${encodeURIComponent(deviceId)}/gaps`))
      .map((row) => ({
        bootId: numberValue(row, "boot_id", "bootId"),
        expectedSeq: numberValue(row, "expected_seq", "expectedSeq"),
        actualSeq: numberValue(row, "actual_seq", "actualSeq"),
        gapSize: numberValue(row, "gap_size", "gapSize"),
        detectedAt: stringValue(row, "detected_at", "detectedAt"),
      })),
  listAlerts: async (): Promise<Alert[]> =>
    (await request<JsonRecord[]>("/api/v1/alerts")).map((row) => normalizeAlert(row)),
  getDeviceAlerts: async (deviceId: string): Promise<Alert[]> =>
    (await request<JsonRecord[]>(`/api/v1/alerts/${encodeURIComponent(deviceId)}`))
      .map((row) => normalizeAlert(row, deviceId)),
  acknowledgeAlert: (id: string): Promise<ActionResult> =>
    request(`/api/v1/alerts/${encodeURIComponent(id)}/acknowledge`, { method: "POST" }),
  resolveAlert: (id: string): Promise<ActionResult> =>
    request(`/api/v1/alerts/${encodeURIComponent(id)}/resolve`, { method: "POST" }),
};
