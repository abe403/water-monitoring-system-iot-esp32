import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { freshness, timeAgo } from "../lib/format";
import type { Device, Reading } from "../lib/types";
import { EmptyState, StatusTag } from "./StateViews";

export function DeviceRoster({
  devices,
  selected,
  latest,
  onSelect,
}: {
  devices: Device[];
  selected: string;
  latest: ReadonlyMap<string, Reading>;
  onSelect?: (deviceId: string) => void;
}) {
  if (!devices.length) {
    return <EmptyState title="No provisioned devices" detail="A device will appear after its first accepted batch." />;
  }

  return (
    <ul className="device-roster">
      {devices.map((device) => {
        const liveReading = latest.get(device.deviceId);
        const seenAt = liveReading?.observedAt || device.lastSeenAt;
        const state = freshness(seenAt);
        return (
          <li key={device.deviceId} className={selected === device.deviceId ? "device-roster__selected" : ""}>
            {onSelect ? (
              <button onClick={() => onSelect(device.deviceId)} aria-pressed={selected === device.deviceId}>
                <span><strong>{device.deviceId}</strong><small>{device.totalReadings.toLocaleString()} stored readings</small></span>
                <span><StatusTag state={state} /><small>{timeAgo(seenAt)}</small></span>
              </button>
            ) : (
              <Link to={`/devices/${encodeURIComponent(device.deviceId)}`}>
                <span><strong>{device.deviceId}</strong><small>{device.totalReadings.toLocaleString()} stored readings</small></span>
                <span><StatusTag state={state} /><ChevronRight aria-hidden="true" /></span>
              </Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}
