export type Quality = "GOOD" | "SUSPECT" | "MISSING" | string;
export type AlertState = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "EXPIRED" | string;

export interface Device {
  deviceId: string;
  hardwareRevision: string | null;
  firmwareVersion: string | null;
  provisioned: boolean;
  firstSeenAt: string;
  lastSeenAt: string;
  totalReadings: number;
  activeAlerts: number;
}

export interface Reading {
  deviceId: string;
  bootId: number;
  seq: number;
  observedAt: string;
  receivedAt: string;
  distanceMm: number | null;
  tempTenths: number | null;
  rssiDbm: number | null;
  levelPct: number | null;
  quality: Quality;
  interarrivalMs?: number;
  wireVersion?: number;
}

export interface HourlyReading {
  deviceId: string;
  bucket: string;
  averageDistanceMm: number | null;
  averageTempTenths: number | null;
  averageRssiDbm: number | null;
  averageLevelPct: number | null;
  minimumLevelPct: number | null;
  maximumLevelPct: number | null;
  sampleCount: number;
}

export interface IngestGap {
  bootId: number;
  expectedSeq: number;
  actualSeq: number;
  gapSize: number;
  detectedAt: string;
}

export interface Alert {
  id: string;
  deviceId: string;
  anomalyType: string;
  state: AlertState;
  score: number | null;
  openedAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
}

export interface ActionResult {
  updated: boolean;
  alertId: string;
}

export type LiveStatus = "connecting" | "live" | "offline";

export type JsonRecord = Record<string, unknown>;
