import { AlertTriangle, Inbox, LoaderCircle, RefreshCw } from "lucide-react";

export function LoadingState({ label = "Loading operational data" }: { label?: string }) {
  return (
    <div className="state-view" role="status">
      <LoaderCircle className="spin" aria-hidden="true" />
      <strong>{label}</strong>
      <span>Keeping the last trustworthy state visible where available.</span>
    </div>
  );
}

export function EmptyState({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="state-view state-view--quiet">
      <Inbox aria-hidden="true" />
      <strong>{title}</strong>
      <span>{detail}</span>
    </div>
  );
}

export function ErrorState({
  title = "Operational data unavailable",
  error,
  retry,
}: {
  title?: string;
  error: Error | null;
  retry?: () => void;
}) {
  return (
    <div className="state-view state-view--error" role="alert">
      <AlertTriangle aria-hidden="true" />
      <strong>{title}</strong>
      <span>{error?.message ?? "The service did not return a usable response."}</span>
      {retry && (
        <button className="text-button" onClick={retry}>
          <RefreshCw aria-hidden="true" /> Retry
        </button>
      )}
    </div>
  );
}

export function StatusTag({ state }: { state: string }) {
  const normalized = state.toLowerCase();
  const kind = ["good", "online", "live", "resolved"].includes(normalized)
    ? "good"
    : ["suspect", "stale", "acknowledged"].includes(normalized)
      ? "warn"
      : ["open", "offline", "missing", "critical"].includes(normalized)
        ? "bad"
        : "neutral";
  return <span className={`status-tag status-tag--${kind}`}><i aria-hidden="true" />{state}</span>;
}
