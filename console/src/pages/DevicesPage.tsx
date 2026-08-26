import { ChevronRight, RadioTower } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "../components/StateViews";
import { freshness, formatDateTime, timeAgo } from "../lib/format";
import { useDevices } from "../lib/queries";
import { useLiveTelemetry } from "../live/useLiveTelemetry";

export function DevicesPage() {
  const query = useDevices();
  const { readings } = useLiveTelemetry();

  return (
    <div className="page">
      <header className="page-header">
        <div><h1>Device fleet</h1><p>Provisioning, recency, stored volume, and alert load for every sensor node.</p></div>
      </header>
      <section className="panel fleet-panel" aria-labelledby="fleet-heading">
        <div className="panel-heading">
          <div><h2 id="fleet-heading">Provisioned devices</h2><p>Sorted by most recent contact</p></div>
          <RadioTower aria-hidden="true" />
        </div>
        {query.isPending && <LoadingState label="Loading the device registry" />}
        {query.isError && <ErrorState error={query.error} retry={() => void query.refetch()} />}
        {query.isSuccess && !query.data.length && <EmptyState title="No devices registered" detail="A sensor appears after its first accepted telemetry batch." />}
        {query.isSuccess && query.data.length > 0 && (
          <div className="table-scroll">
            <table className="fleet-table">
              <thead><tr><th>Device</th><th>Status</th><th>Last contact</th><th>Firmware</th><th>Readings</th><th>Alerts</th><th><span className="sr-only">Open</span></th></tr></thead>
              <tbody>
                {query.data.map((device) => {
                  const live = readings.get(device.deviceId);
                  const seenAt = live?.observedAt || device.lastSeenAt;
                  const state = freshness(seenAt);
                  return (
                    <tr key={device.deviceId}>
                      <td><Link className="table-primary-link" to={`/devices/${encodeURIComponent(device.deviceId)}`}>{device.deviceId}</Link><small>First seen {formatDateTime(device.firstSeenAt)}</small></td>
                      <td><StatusTag state={state} /></td>
                      <td>{timeAgo(seenAt)}<small>{formatDateTime(seenAt)}</small></td>
                      <td>{device.firmwareVersion ?? "Not reported"}<small>{device.hardwareRevision ?? "Hardware revision unknown"}</small></td>
                      <td>{device.totalReadings.toLocaleString()}</td>
                      <td>{device.activeAlerts}</td>
                      <td><Link className="row-action" to={`/devices/${encodeURIComponent(device.deviceId)}`} aria-label={`Open ${device.deviceId}`}><ChevronRight aria-hidden="true" /></Link></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
