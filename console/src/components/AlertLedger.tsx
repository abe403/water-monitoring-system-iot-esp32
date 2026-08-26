import { CheckCircle2, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { formatDateTime, humanize } from "../lib/format";
import type { Alert } from "../lib/types";
import { StatusTag } from "./StateViews";

export function AlertLedger({ alerts, limit = 4 }: { alerts: Alert[]; limit?: number }) {
  if (!alerts.length) {
    return (
      <div className="all-clear">
        <CheckCircle2 aria-hidden="true" />
        <strong>No active alerts</strong>
        <span>All monitored devices are clear.</span>
        <Link to="/alerts">Review alert workspace <ChevronRight aria-hidden="true" /></Link>
      </div>
    );
  }

  return (
    <div className="alert-ledger">
      <ul>
        {alerts.slice(0, limit).map((alert) => (
          <li key={alert.id}>
            <Link to={`/alerts?device=${encodeURIComponent(alert.deviceId)}`}>
              <span><strong>{humanize(alert.anomalyType)}</strong><small>{alert.deviceId} · {formatDateTime(alert.openedAt)}</small></span>
              <StatusTag state={alert.state} />
            </Link>
          </li>
        ))}
      </ul>
      <Link className="ledger-link" to="/alerts">Open alert workspace <ChevronRight aria-hidden="true" /></Link>
    </div>
  );
}
