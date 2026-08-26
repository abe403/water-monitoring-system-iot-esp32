import { Client } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";
import SockJS from "sockjs-client";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";
import { api, normalizeReading } from "../lib/api";
import type { JsonRecord, LiveStatus, Reading } from "../lib/types";
import { LiveContext, type LiveContextValue } from "./context";

function socketUrl(): string {
  if (api.base) return `${api.base}/ws`;
  return `${window.location.origin}/ws`;
}

export function LiveTelemetryProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const clientRef = useRef<Client | null>(null);
  const [generation, setGeneration] = useState(0);
  const [status, setStatus] = useState<LiveStatus>("connecting");
  const [lastEventAt, setLastEventAt] = useState<string | null>(null);
  const [readings, setReadings] = useState<Map<string, Reading>>(() => new Map());

  useEffect(() => {
    setStatus("connecting");
    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl()),
      reconnectDelay: 5_000,
      heartbeatIncoming: 20_000,
      heartbeatOutgoing: 20_000,
      connectionTimeout: 10_000,
      debug: import.meta.env.DEV ? (message) => console.debug("[stomp]", message) : () => undefined,
      onConnect: () => {
        setStatus("live");
        client.subscribe("/topic/telemetry", (message) => {
          try {
            const reading = normalizeReading(JSON.parse(message.body) as JsonRecord);
            if (!reading.deviceId) return;
            const received = new Date().toISOString();
            setLastEventAt(received);
            setReadings((current) => {
              const next = new Map(current);
              next.set(reading.deviceId, reading);
              return next;
            });
            queryClient.setQueryData<Reading[]>(["readings", reading.deviceId, 24], (current = []) =>
              [reading, ...current.filter((item) => !(item.bootId === reading.bootId && item.seq === reading.seq))].slice(0, 1000),
            );
            void queryClient.invalidateQueries({ queryKey: ["devices"] });
          } catch (error) {
            console.error("Ignoring malformed live telemetry", error);
          }
        });
      },
      onStompError: () => setStatus("offline"),
      onWebSocketClose: () => setStatus("offline"),
      onWebSocketError: () => setStatus("offline"),
    });

    clientRef.current = client;
    client.activate();
    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [generation, queryClient]);

  const reconnect = useCallback(() => {
    void clientRef.current?.deactivate().finally(() => setGeneration((value) => value + 1));
  }, []);

  const context = useMemo<LiveContextValue>(() => ({
    status,
    lastEventAt,
    readings,
    reconnect,
  }), [lastEventAt, readings, reconnect, status]);

  return <LiveContext.Provider value={context}>{children}</LiveContext.Provider>;
}
