import { distance, level, temperature, timeAgo } from "../lib/format";
import type { Reading } from "../lib/types";
import { StatusTag } from "./StateViews";

export function TankGauge({ reading, deviceId }: { reading?: Reading; deviceId: string }) {
  const percentage = Math.min(100, Math.max(0, reading?.levelPct ?? 0));
  const hasLevel = reading?.levelPct !== null && reading?.levelPct !== undefined;

  return (
    <section className="tank-station" aria-labelledby="tank-heading">
      <div className="tank-station__header">
        <div>
          <h2 id="tank-heading">{deviceId || "No device selected"}</h2>
          <p>Calibrated tank level</p>
        </div>
        {reading && <StatusTag state={reading.quality} />}
      </div>
      <div className="tank-assembly">
        <div className="staff-gauge" aria-hidden="true">
          {[100, 75, 50, 25, 0].map((mark) => <span key={mark} style={{ bottom: `${mark}%` }}>{mark}</span>)}
        </div>
        <div
          className={`tank-vessel ${hasLevel ? "" : "tank-vessel--empty"}`}
          role="img"
          aria-label={hasLevel ? `Tank level ${level(reading.levelPct)}` : "No calibrated tank level available"}
        >
          <div className="tank-vessel__cap" aria-hidden="true" />
          <div className="tank-vessel__water" style={{ transform: `scaleY(${percentage / 100})` }} aria-hidden="true">
            <span />
          </div>
          <div className="tank-vessel__reading">
            <strong>{level(reading?.levelPct)}</strong>
            <span>{hasLevel ? "level" : "awaiting reading"}</span>
          </div>
          <div className="tank-vessel__feet" aria-hidden="true"><i /><i /></div>
        </div>
      </div>
      <dl className="measurement-register">
        <div><dt>Measured</dt><dd>{timeAgo(reading?.observedAt)}</dd></div>
        <div><dt>Distance</dt><dd>{distance(reading?.distanceMm)}</dd></div>
        <div><dt>Temperature</dt><dd>{temperature(reading?.tempTenths)}</dd></div>
        <div><dt>Signal</dt><dd>{reading?.rssiDbm == null ? "—" : `${reading.rssiDbm} dBm`}</dd></div>
      </dl>
    </section>
  );
}
