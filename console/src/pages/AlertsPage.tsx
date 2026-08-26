import { Check, CheckCheck, ExternalLink, ShieldAlert, X } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "../components/StateViews";
import { formatDateTime, humanize } from "../lib/format";
import { useAlertAction, useAlerts } from "../lib/queries";
import type { Alert } from "../lib/types";

type AlertFilter = "ACTIVE" | "OPEN" | "ACKNOWLEDGED";

function AlertRow({ alert }: { alert: Alert }) {
  const acknowledge = useAlertAction("acknowledge");
  const resolve = useAlertAction("resolve");
  const [confirmResolve, setConfirmResolve] = useState(false);
  const busy = acknowledge.isPending || resolve.isPending;
  const actionError = acknowledge.error ?? resolve.error;

  return (
    <li className="alert-row">
      <div className="alert-row__identity">
        <ShieldAlert aria-hidden="true" />
        <div><strong>{humanize(alert.anomalyType)}</strong><span>{alert.deviceId}</span></div>
      </div>
      <dl className="alert-row__facts">
        <div><dt>Opened</dt><dd>{formatDateTime(alert.openedAt)}</dd></div>
        <div><dt>Score</dt><dd>{alert.score?.toFixed(3) ?? "Not supplied"}</dd></div>
        <div><dt>State</dt><dd><StatusTag state={alert.state} /></dd></div>
      </dl>
      <div className="alert-row__actions">
        <Link className="text-button" to={`/devices/${encodeURIComponent(alert.deviceId)}`}>Inspect device <ExternalLink aria-hidden="true" /></Link>
        {alert.state === "OPEN" && (
          <button className="secondary-button" onClick={() => acknowledge.mutate(alert.id)} disabled={busy}>
            <Check aria-hidden="true" /> Acknowledge
          </button>
        )}
        {!confirmResolve ? (
          <button className="primary-button" onClick={() => setConfirmResolve(true)} disabled={busy}>
            <CheckCheck aria-hidden="true" /> Resolve
          </button>
        ) : (
          <div className="resolve-confirm" role="group" aria-label="Confirm resolution">
            <span>Confirm resolved?</span>
            <button className="primary-button" onClick={() => resolve.mutate(alert.id)} disabled={busy}>Yes, resolve</button>
            <button className="icon-button" onClick={() => setConfirmResolve(false)} aria-label="Cancel resolution"><X aria-hidden="true" /></button>
          </div>
        )}
      </div>
      {actionError && <p className="inline-error" role="alert">Action failed: {actionError.message}. Refresh and try again.</p>}
    </li>
  );
}

export function AlertsPage() {
  const query = useAlerts();
  const [filter, setFilter] = useState<AlertFilter>("ACTIVE");
  const [params] = useSearchParams();
  const deviceFilter = params.get("device");
  const alerts = useMemo(() => (query.data ?? []).filter((alert) => {
    if (deviceFilter && alert.deviceId !== deviceFilter) return false;
    return filter === "ACTIVE" || alert.state === filter;
  }), [deviceFilter, filter, query.data]);

  return (
    <div className="page alerts-page">
      <header className="page-header">
        <div><h1>Alert workspace</h1><p>Acknowledge ownership, inspect evidence, and close resolved anomaly events.</p></div>
      </header>
      <section className="panel alerts-workspace" aria-labelledby="alert-queue-heading">
        <div className="panel-heading alert-toolbar">
          <div><h2 id="alert-queue-heading">Active queue</h2><p>{deviceFilter ? `Filtered to ${deviceFilter}` : "All monitored devices"}</p></div>
          <div className="range-tabs" aria-label="Alert state filter">
            {(["ACTIVE", "OPEN", "ACKNOWLEDGED"] as AlertFilter[]).map((state) => (
              <button key={state} onClick={() => setFilter(state)} aria-pressed={filter === state}>{state}</button>
            ))}
          </div>
        </div>
        {query.isPending && <LoadingState label="Loading active alerts" />}
        {query.isError && <ErrorState title="Alert queue unavailable" error={query.error} retry={() => void query.refetch()} />}
        {query.isSuccess && !alerts.length && (
          <EmptyState
            title={deviceFilter ? `No active alerts for ${deviceFilter}` : "No alerts need attention"}
            detail="The active anomaly queue is clear. This view refreshes every 30 seconds."
          />
        )}
        {query.isSuccess && alerts.length > 0 && <ul className="alert-list">{alerts.map((alert) => <AlertRow key={alert.id} alert={alert} />)}</ul>}
      </section>
      <aside className="safety-note">
        <ShieldAlert aria-hidden="true" />
        <div><strong>Pump command remains intentionally unavailable</strong><p>This console will expose pump operation only after the platform provides an authenticated, fail-safe command API with delivery confirmation.</p></div>
      </aside>
    </div>
  );
}
