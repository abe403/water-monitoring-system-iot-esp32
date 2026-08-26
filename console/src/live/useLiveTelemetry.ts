import { useContext } from "react";
import { LiveContext, type LiveContextValue } from "./context";

export function useLiveTelemetry(): LiveContextValue {
  const context = useContext(LiveContext);
  if (!context) throw new Error("useLiveTelemetry must be used inside LiveTelemetryProvider");
  return context;
}
