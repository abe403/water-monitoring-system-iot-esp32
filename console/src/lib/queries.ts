import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";

export const useDevices = () => useQuery({
  queryKey: ["devices"],
  queryFn: api.listDevices,
  refetchInterval: 30_000,
});

export const useDevice = (deviceId: string) => useQuery({
  queryKey: ["device", deviceId],
  queryFn: () => api.getDevice(deviceId),
  enabled: Boolean(deviceId),
});

export const useReadings = (deviceId: string, hours: number) => useQuery({
  queryKey: ["readings", deviceId, hours],
  queryFn: () => api.getReadings(deviceId, hours),
  enabled: Boolean(deviceId),
  refetchInterval: 60_000,
});

export const useHourlyReadings = (deviceId: string, hours: number) => useQuery({
  queryKey: ["hourly", deviceId, hours],
  queryFn: () => api.getHourly(deviceId, hours),
  enabled: Boolean(deviceId),
  staleTime: 5 * 60_000,
});

export const useGaps = (deviceId: string) => useQuery({
  queryKey: ["gaps", deviceId],
  queryFn: () => api.getGaps(deviceId),
  enabled: Boolean(deviceId),
  refetchInterval: 60_000,
});

export const useAlerts = () => useQuery({
  queryKey: ["alerts"],
  queryFn: api.listAlerts,
  refetchInterval: 30_000,
});

export const useDeviceAlerts = (deviceId: string) => useQuery({
  queryKey: ["device-alerts", deviceId],
  queryFn: () => api.getDeviceAlerts(deviceId),
  enabled: Boolean(deviceId),
  refetchInterval: 30_000,
});

export function useAlertAction(action: "acknowledge" | "resolve") {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => action === "acknowledge"
      ? api.acknowledgeAlert(id)
      : api.resolveAlert(id),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["alerts"] }),
        queryClient.invalidateQueries({ queryKey: ["device-alerts"] }),
        queryClient.invalidateQueries({ queryKey: ["devices"] }),
      ]);
    },
  });
}
