import { createContext } from "react";
import type { LiveStatus, Reading } from "../lib/types";

export interface LiveContextValue {
  status: LiveStatus;
  lastEventAt: string | null;
  readings: ReadonlyMap<string, Reading>;
  reconnect: () => void;
}

export const LiveContext = createContext<LiveContextValue | null>(null);
