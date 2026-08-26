import { ArrowRight, Clock3 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AlertLedger } from "../components/AlertLedger";
import { DeliveryChain } from "../components/DeliveryChain";
import { DeviceRoster } from "../components/DeviceRoster";
import { ErrorState, LoadingState, StatusTag } from "../components/StateViews";
import { TankGauge } from "../components/TankGauge";
import { TelemetryChart } from "../components/TelemetryChart";
import { formatTime, level, temperature } from "../lib/format";
import { useAlerts, useDevices, useReadings } from "../lib/queries";
import { useLiveTelemetry } from "../live/useLiveTelemetry";

export function OverviewPage() {
  const devicesQuery = useDevices();
  const alertsQuery = useAlerts();
  const [selectedDevice, setSelectedDevice] = useState("");
  const { readings: liveReadings, status: liveStatus } = useLiveTelemetry();

  useEffect(() => {
    if (!selectedDevice && devicesQuery.data?.[0]) setSelectedDevice(devicesQuery.data[0].deviceId);
  }, [devicesQuery.data, selectedDevice]);

  const readingsQuery = useReadings(selectedDevice, 24);
  const selectedReading = liveReadings.get(selectedDevice) ?? readingsQuery.data?.[0];
  const readings = useMemo(() => {
    const stored = readingsQuery.data ?? [];
    if (!selectedReading) return stored;
    return [selectedReading, ...stored.filter((item) => !(item.bootId === selectedReading.bootId && item.seq === selectedReading.seq))];
  }, [readingsQuery.data, selectedReading]);

  if (devicesQuery.isPending) return <LoadingState label="Loading the device fleet" />;
  if (devicesQuery.isError) {
    return <ErrorState error={devicesQuery.error} retry={() => void devicesQuery.refetch()} />;
  }

  const devices = devicesQuery.data;

  return (
    <div className="page overview-page">
      <header className="page-header">
        <div>
          <h1>Operations overview</h1>
          <p>Committed readings, delivery evidence, and active attention across {devices.length} {devices.length === 1 ? "device" : "devices"}.</p>
        </div>
        <Link className="header-action" to={selectedDevice ? `/devices/${encodeURIComponent(selectedDevice)}` : "/devices"}>
          Inspect device <ArrowRight aria-hidden="true" />
        </Link>
      </header>

      <div className="overview-grid">
        <div className="overview-grid__tank">
          <TankGauge reading={selectedReading} deviceId={selectedDevice} />
        </div>

        <div className="overview-grid__chain">
          <DeliveryChain reading={selectedReading} liveStatus={liveStatus} hasLiveReading={liveReadings.has(selectedDevice)} />
        </div>

        <section className="panel overview-grid__alerts" aria-labelledby="overview-alerts">
          <div className="panel-heading">
            <div><h2 id="overview-alerts">Active alerts</h2><p>Operator attention queue</p></div>
            <strong className="panel-count">{alertsQuery.isSuccess ? alertsQuery.data.length : "—"}</strong>
          </div>
          {alertsQuery.isPending && <LoadingState label="Loading active alerts" />}
          {alertsQuery.isError && <ErrorState title="Alerts unavailable" error={alertsQuery.error} retry={() => void alertsQuery.refetch()} />}
          {alertsQuery.isSuccess && <AlertLedger alerts={alertsQuery.data} />}
        </section>

        <section className="panel overview-grid__devices" aria-labelledby="overview-devices">
          <div className="panel-heading">
            <div><h2 id="overview-devices">Device roster</h2><p>Select the tank in view</p></div>
            <strong className="panel-count">{devices.length}</strong>
          </div>
          <DeviceRoster devices={devices} selected={selectedDevice} latest={liveReadings} onSelect={setSelectedDevice} />
          <Link className="ledger-link" to="/devices">View fleet details <ArrowRight aria-hidden="true" /></Link>
        </section>

        <section className="panel overview-grid__chart" aria-labelledby="history-heading">
          <div className="panel-heading panel-heading--chart">
            <div><h2 id="history-heading">Level and environment</h2><p>Past 24 hours · {selectedDevice}</p></div>
            <span className="live-marker"><i aria-hidden="true" />{liveStatus === "live" ? "Listening live" : "Stored history"}</span>
          </div>
          {readingsQuery.isPending && <LoadingState label="Loading telemetry history" />}
          {readingsQuery.isError && <ErrorState title="Telemetry unavailable" error={readingsQuery.error} retry={() => void readingsQuery.refetch()} />}
          {readingsQuery.isSuccess && <TelemetryChart readings={readings} label={`24-hour telemetry for ${selectedDevice}`} />}
        </section>

        <section className="panel overview-grid__readings" aria-labelledby="recent-heading">
          <div className="panel-heading">
            <div><h2 id="recent-heading">Recent readings</h2><p>Newest committed samples</p></div>
            <Clock3 aria-hidden="true" />
          </div>
          {readingsQuery.isPending && <LoadingState label="Loading recent readings" />}
          {readingsQuery.isError && <ErrorState title="Recent readings unavailable" error={readingsQuery.error} retry={() => void readingsQuery.refetch()} />}
          {readingsQuery.isSuccess && <div className="table-scroll">
            <table>
              <thead><tr><th>Time</th><th>Level</th><th>Temp</th><th>RSSI</th><th>Quality</th></tr></thead>
              <tbody>
                {readings.slice(0, 8).map((reading) => (
                  <tr key={`${reading.bootId}-${reading.seq}`}>
                    <td>{formatTime(reading.observedAt)}</td>
                    <td>{level(reading.levelPct)}</td>
                    <td>{temperature(reading.tempTenths)}</td>
                    <td>{reading.rssiDbm == null ? "—" : `${reading.rssiDbm} dBm`}</td>
                    <td><StatusTag state={reading.quality} /></td>
                  </tr>
                ))}
                {!readings.length && <tr><td colSpan={5} className="table-empty">No committed readings in this window.</td></tr>}
              </tbody>
            </table>
          </div>}
        </section>
      </div>
    </div>
  );
}
