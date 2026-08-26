const dateTime = new Intl.DateTimeFormat(undefined, {
  month: "short",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const time = new Intl.DateTimeFormat(undefined, {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

export function formatDateTime(input: string | null | undefined): string {
  if (!input) return "Not recorded";
  const date = new Date(input);
  return Number.isNaN(date.getTime()) ? "Not recorded" : dateTime.format(date);
}

export function formatTime(input: string | null | undefined): string {
  if (!input) return "—";
  const date = new Date(input);
  return Number.isNaN(date.getTime()) ? "—" : time.format(date);
}

export function timeAgo(input: string | null | undefined, now = Date.now()): string {
  if (!input) return "never";
  const timestamp = new Date(input).getTime();
  if (Number.isNaN(timestamp)) return "unknown";
  const seconds = Math.max(0, Math.round((now - timestamp) / 1000));
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export function freshness(input: string | null | undefined, now = Date.now()): "online" | "stale" | "offline" {
  if (!input) return "offline";
  const age = now - new Date(input).getTime();
  if (!Number.isFinite(age)) return "offline";
  if (age <= 5 * 60_000) return "online";
  if (age <= 60 * 60_000) return "stale";
  return "offline";
}

export const level = (value: number | null | undefined): string =>
  value === null || value === undefined ? "—" : `${value.toFixed(1)}%`;

export const temperature = (tenths: number | null | undefined): string =>
  tenths === null || tenths === undefined ? "—" : `${(tenths / 10).toFixed(1)}°C`;

export const distance = (millimetres: number | null | undefined): string =>
  millimetres === null || millimetres === undefined ? "—" : `${millimetres} mm`;

export const rssi = (value: number | null | undefined): string =>
  value === null || value === undefined ? "—" : `${value} dBm`;

export function humanize(value: string): string {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}
