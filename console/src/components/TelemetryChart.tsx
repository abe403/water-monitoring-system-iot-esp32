import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatDateTime, formatTime } from "../lib/format";
import type { Reading } from "../lib/types";
import { EmptyState } from "./StateViews";

interface ChartDatum {
  timestamp: number;
  observedAt: string;
  level: number | null;
  temperature: number | null;
  rssi: number | null;
  minimum: number | null;
  maximum: number | null;
}

function chartData(readings: Reading[]): ChartDatum[] {
  return [...readings]
    .sort((a, b) => new Date(a.observedAt).getTime() - new Date(b.observedAt).getTime())
    .map((reading) => ({
      timestamp: new Date(reading.observedAt).getTime(),
      observedAt: reading.observedAt,
      level: reading.levelPct,
      temperature: reading.tempTenths == null ? null : reading.tempTenths / 10,
      rssi: reading.rssiDbm,
      minimum: reading.levelPct == null ? null : Math.max(0, reading.levelPct - 1.5),
      maximum: reading.levelPct == null ? null : Math.min(100, reading.levelPct + 1.5),
    }))
    .filter((item) => Number.isFinite(item.timestamp));
}

export function TelemetryChart({ readings, label }: { readings: Reading[]; label: string }) {
  const data = chartData(readings);
  const levelValues = data.flatMap((item) => item.level == null ? [] : [item.level]);
  const minimum = levelValues.length ? Math.min(...levelValues) : null;
  const maximum = levelValues.length ? Math.max(...levelValues) : null;
  const latest = data.at(-1)!;
  const first = data[0]!;
  const metrics = [
    { name: "Level", unit: "%", values: data.flatMap((item) => item.level == null ? [] : [item.level]) },
    { name: "Temperature", unit: "°C", values: data.flatMap((item) => item.temperature == null ? [] : [item.temperature]) },
    { name: "RSSI", unit: " dBm", values: data.flatMap((item) => item.rssi == null ? [] : [item.rssi]) },
  ];

  if (!data.length) {
    return <EmptyState title="No readings in this window" detail="The chart will begin when committed telemetry reaches the store." />;
  }

  return (
    <div className="telemetry-chart">
      <p className="sr-only">
        {label}. {data.length} readings from {formatDateTime(first.observedAt)} to {formatDateTime(latest.observedAt)}.
      </p>
      <table className="sr-only">
        <caption>Telemetry series summary</caption>
        <thead><tr><th>Series</th><th>Latest</th><th>Minimum</th><th>Maximum</th><th>Trend across window</th></tr></thead>
        <tbody>
          {metrics.map((metric) => {
            const metricFirst = metric.values[0];
            const metricLatest = metric.values.at(-1);
            const trend = metricFirst == null || metricLatest == null ? null : metricLatest - metricFirst;
            return <tr key={metric.name}>
              <th>{metric.name}</th>
              <td>{metricLatest == null ? "Unavailable" : `${metricLatest.toFixed(1)}${metric.unit}`}</td>
              <td>{metric.values.length ? `${Math.min(...metric.values).toFixed(1)}${metric.unit}` : "Unavailable"}</td>
              <td>{metric.values.length ? `${Math.max(...metric.values).toFixed(1)}${metric.unit}` : "Unavailable"}</td>
              <td>{trend == null ? "Unavailable" : `${trend >= 0 ? "+" : ""}${trend.toFixed(1)}${metric.unit}`}</td>
            </tr>;
          })}
        </tbody>
      </table>
      <div className="chart-legend" aria-hidden="true">
        <span className="chart-legend__level">Level</span>
        <span className="chart-legend__temp">Temperature</span>
        <span className="chart-legend__rssi">RSSI</span>
      </div>
      <div className="chart-canvas" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 12, right: 8, bottom: 4, left: -12 }}>
            <CartesianGrid stroke="#b7c4c0" strokeDasharray="2 5" vertical={false} />
            <XAxis
              dataKey="timestamp"
              type="number"
              scale="time"
              domain={["dataMin", "dataMax"]}
              tickFormatter={(stamp) => formatTime(new Date(stamp).toISOString()).slice(0, 5)}
              minTickGap={42}
              tick={{ fill: "#30464d", fontSize: 11 }}
              axisLine={{ stroke: "#71858a" }}
              tickLine={false}
            />
            <YAxis
              yAxisId="level"
              domain={[0, 100]}
              tickFormatter={(tick) => `${tick}%`}
              tick={{ fill: "#30464d", fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              width={50}
            />
            <YAxis yAxisId="temp" hide domain={["auto", "auto"]} />
            <YAxis yAxisId="rssi" hide domain={[-110, -20]} />
            <Tooltip
              labelFormatter={(stamp) => formatDateTime(new Date(Number(stamp)).toISOString())}
              formatter={(rawValue, name) => {
                const numeric = Number(rawValue);
                if (name === "level") return [`${numeric.toFixed(1)}%`, "Level"];
                if (name === "temperature") return [`${numeric.toFixed(1)}°C`, "Temperature"];
                if (name === "rssi") return [`${numeric.toFixed(0)} dBm`, "RSSI"];
                return [String(rawValue), String(name)];
              }}
              contentStyle={{ borderRadius: 2, border: "1px solid #71858a", background: "#f5f7f2" }}
            />
            <Area yAxisId="level" dataKey="maximum" stroke="none" fill="#79aaa4" fillOpacity={0.12} isAnimationActive={false} />
            <Area yAxisId="level" dataKey="minimum" stroke="none" fill="#f5f7f2" fillOpacity={1} isAnimationActive={false} />
            <Line yAxisId="level" dataKey="level" stroke="#087e78" strokeWidth={3} dot={false} connectNulls isAnimationActive={false} />
            <Line yAxisId="temp" dataKey="temperature" stroke="#b96812" strokeWidth={1.5} dot={false} connectNulls isAnimationActive={false} />
            <Line yAxisId="rssi" dataKey="rssi" stroke="#425d9b" strokeWidth={1.5} dot={false} connectNulls isAnimationActive={false} />
            {latest && <ReferenceLine yAxisId="level" x={latest.timestamp} stroke="#092534" strokeDasharray="4 4" label={{ value: "LIVE", position: "insideTopRight", fill: "#092534", fontSize: 11 }} />}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <dl className="chart-summary">
        <div><dt>Now</dt><dd>{latest?.level == null ? "—" : `${latest.level.toFixed(1)}%`}</dd></div>
        <div><dt>Minimum</dt><dd>{minimum == null ? "—" : `${minimum.toFixed(1)}%`}</dd></div>
        <div><dt>Maximum</dt><dd>{maximum == null ? "—" : `${maximum.toFixed(1)}%`}</dd></div>
        <div><dt>Samples</dt><dd>{data.length.toLocaleString()}</dd></div>
      </dl>
    </div>
  );
}
