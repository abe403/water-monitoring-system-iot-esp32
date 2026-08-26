import { ArrowLeft, Cable, Clock3, ShieldCheck } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { DeliveryChain } from "../components/DeliveryChain";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "../components/StateViews";
import { TankGauge } from "../components/TankGauge";
import { TelemetryChart } from "../components/TelemetryChart";
import { formatDateTime, formatTime, level, temperature } from "../lib/format";
import { useDevice, useDeviceAlerts, useGaps, useReadings } from "../lib/queries";
import { useLiveTelemetry } from "../live/useLiveTelemetry";

const ranges = [1, 6, 24, 168] as const;

export function DeviceDetailPage() {
  const { deviceId = "" } = useParams();
  const [hours, setHours] = useState<number>(24);
  const deviceQuery = useDevice(deviceId);
  const readingsQuery = useReadings(deviceId, hours);
  const gapsQuery = useGaps(deviceId);
  const alertsQuery = useDeviceAlerts(deviceId);
  const { readings: liveReadings, status: liveStatus } = useLiveTelemetry();
  const liveReading = liveReadings.get(deviceId);
  const readings = useMemo(() => {
    const stored = readingsQuery.data ?? [];
    if (!liveReading) return stored;
    return [liveReading, ...stored.filter((item) => !(item.bootId === liveReading.bootId && item.seq === liveReading.seq))];
  }, [liveReading, readingsQuery.data]);
  const latest = liveReading ?? readings[0];

  if (deviceQuery.isPending) return <LoadingState label={`Loading ${deviceId}`} />;
  if (deviceQuery.isError) return <ErrorState title="Device not available" error={deviceQuery.error} retry={() => void deviceQuery.refetch()} />;

  return (
    <div className="page device-detail-page">
      <Link className="back-link" to="/devices"><ArrowLeft aria-hidden="true" /> Device fleet</Link>
      <header className="page-header device-title">
        <div><h1>{deviceId}</h1><p>First seen {formatDateTime(deviceQuery.data.firstSeenAt)} · firmware {deviceQuery.data.firmwareVersion ?? "not reported"}</p></div>
        <StatusTag state={latest?.quality ?? "No reading"} />
      </header>

      <div className="device-grid">
        <TankGauge reading={latest} deviceId={deviceId} />
        <DeliveryChain reading={latest} liveStatus={liveStatus} hasLiveReading={Boolean(liveReading)} />

        <section className="panel device-grid__history" aria-labelledby="device-history">
          <div className="panel-heading panel-heading--chart">
            <div><h2 id="device-history">Telemetry history</h2><p>Calibrated level with environment context</p></div>
            <div className="range-tabs" aria-label="History range">
              {ranges.map((range) => (
                <button key={range} onClick={() => setHours(range)} aria-pressed={hours === range}>
                  {range === 168 ? "7D" : `${range}H`}
                </button>
              ))}
            </div>
          </div>
          {readingsQuery.isPending && <LoadingState label="Loading device history" />}
          {readingsQuery.isError && <ErrorState title="Telemetry unavailable" error={readingsQuery.error} retry={() => void readingsQuery.refetch()} />}
          {readingsQuery.isSuccess && <TelemetryChart readings={readings} label={`${hours}-hour telemetry for ${deviceId}`} />}
        </section>

        <section className="panel device-grid__integrity" aria-labelledby="integrity-heading">
          <div className="panel-heading"><div><h2 id="integrity-heading">Sequence integrity</h2><p>Detected gaps, newest first</p></div><ShieldCheck aria-hidden="true" /></div>
          {gapsQuery.isPending && <LoadingState label="Checking sequence history" />}
          {gapsQuery.isError && <ErrorState title="Gap history unavailable" error={gapsQuery.error} retry={() => void gapsQuery.refetch()} />}
          {gapsQuery.isSuccess && !gapsQuery.data.length && <EmptyState title="No sequence gaps recorded" detail="Stored gap evidence is clear for this device." />}
          {gapsQuery.isSuccess && gapsQuery.data.length > 0 && (
            <div className="table-scroll"><table><thead><tr><th>Detected</th><th>Boot</th><th>Expected</th><th>Actual</th><th>Missing</th></tr></thead><tbody>
              {gapsQuery.data.map((gap) => <tr key={`${gap.bootId}-${gap.expectedSeq}-${gap.actualSeq}`}><td>{formatDateTime(gap.detectedAt)}</td><td>{gap.bootId}</td><td>{gap.expectedSeq}</td><td>{gap.actualSeq}</td><td>{gap.gapSize}</td></tr>)}
            </tbody></table></div>
          )}
        </section>

        <section className="panel device-grid__alerts" aria-labelledby="device-alerts-heading">
          <div className="panel-heading"><div><h2 id="device-alerts-heading">Alert history</h2><p>Lifecycle events for this device</p></div><Cable aria-hidden="true" /></div>
          {alertsQuery.isPending && <LoadingState label="Loading alert history" />}
          {alertsQuery.isError && <ErrorState title="Alert history unavailable" error={alertsQuery.error} retry={() => void alertsQuery.refetch()} />}
          {alertsQuery.isSuccess && !alertsQuery.data.length && <EmptyState title="No alerts recorded" detail="No anomaly lifecycle events exist for this device." />}
          {alertsQuery.isSuccess && alertsQuery.data.length > 0 && <div className="table-scroll"><table><thead><tr><th>Opened</th><th>Type</th><th>Score</th><th>State</th></tr></thead><tbody>
            {alertsQuery.data.map((alert) => <tr key={alert.id}><td>{formatDateTime(alert.openedAt)}</td><td>{alert.anomalyType.replaceAll("_", " ")}</td><td>{alert.score?.toFixed(3) ?? "—"}</td><td><StatusTag state={alert.state} /></td></tr>)}
          </tbody></table></div>}
        </section>

        <section className="panel device-grid__readings" aria-labelledby="device-readings-heading">
          <div className="panel-heading"><div><h2 id="device-readings-heading">Reading register</h2><p>Latest persisted samples</p></div><Clock3 aria-hidden="true" /></div>
          {readingsQuery.isPending && <LoadingState label="Loading reading register" />}
          {readingsQuery.isError && <ErrorState title="Reading register unavailable" error={readingsQuery.error} retry={() => void readingsQuery.refetch()} />}
          {readingsQuery.isSuccess && <div className="table-scroll"><table><thead><tr><th>Observed</th><th>Level</th><th>Temperature</th><th>RSSI</th><th>Seq</th><th>Quality</th></tr></thead><tbody>
            {readings.slice(0, 20).map((reading) => <tr key={`${reading.bootId}-${reading.seq}`}><td>{formatTime(reading.observedAt)}</td><td>{level(reading.levelPct)}</td><td>{temperature(reading.tempTenths)}</td><td>{reading.rssiDbm ?? "—"}</td><td>{reading.seq}</td><td><StatusTag state={reading.quality} /></td></tr>)}
            {!readings.length && <tr><td colSpan={6} className="table-empty">No samples in the selected window.</td></tr>}
          </tbody></table></div>}
        </section>
      </div>
    </div>
  );
}
