import { Check, Database, Radio, Server, SlidersHorizontal, Waves } from "lucide-react";
import type { LiveStatus, Reading } from "../lib/types";
import { formatTime } from "../lib/format";

const stages = [
  { label: "Device", icon: Radio },
  { label: "Ingest", icon: Waves },
  { label: "Kafka", icon: Server },
  { label: "Calibrate", icon: SlidersHorizontal },
  { label: "Store", icon: Database },
  { label: "Live", icon: Check },
];

export function DeliveryChain({
  reading,
  liveStatus,
  hasLiveReading,
}: {
  reading?: Reading;
  liveStatus: LiveStatus;
  hasLiveReading: boolean;
}) {
  const committed = Boolean(reading);
  return (
    <section className="chain-panel" aria-labelledby="chain-heading">
      <div className="section-heading">
        <div>
          <h2 id="chain-heading">Reading delivery chain</h2>
          <p>{committed ? `Last committed sequence ${reading?.seq}` : "Waiting for a committed reading"}</p>
        </div>
        <span className="chain-timestamp">{reading ? formatTime(reading.receivedAt) : "—"}</span>
      </div>
      <ol className="delivery-chain">
        {stages.map(({ label, icon: Icon }, index) => {
          const liveStage = index === stages.length - 1;
          const available = committed && (!liveStage || (liveStatus === "live" && hasLiveReading));
          const stageLabel = available
            ? "Confirmed"
            : liveStage && committed && liveStatus === "live"
              ? "Awaiting event"
              : liveStage && committed
                ? "Reconnect"
                : "Waiting";
          return (
            <li key={label} className={available ? "chain-stage--ok" : "chain-stage--waiting"}>
              <span className="chain-stage__icon"><Icon aria-hidden="true" /></span>
              <strong>{label}</strong>
              <span>{stageLabel}</span>
            </li>
          );
        })}
      </ol>
      <p className="chain-note">Stages represent evidence carried by the latest persisted reading and the browser’s live subscription—not direct infrastructure health probes.</p>
    </section>
  );
}
