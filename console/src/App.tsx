import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { lazy, Suspense } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { LoadingState } from "./components/StateViews";
import { LiveTelemetryProvider } from "./live/LiveTelemetryProvider";

const OverviewPage = lazy(() => import("./pages/OverviewPage").then((module) => ({ default: module.OverviewPage })));
const DevicesPage = lazy(() => import("./pages/DevicesPage").then((module) => ({ default: module.DevicesPage })));
const DeviceDetailPage = lazy(() => import("./pages/DeviceDetailPage").then((module) => ({ default: module.DeviceDetailPage })));
const AlertsPage = lazy(() => import("./pages/AlertsPage").then((module) => ({ default: module.AlertsPage })));
const NotFoundPage = lazy(() => import("./pages/NotFoundPage").then((module) => ({ default: module.NotFoundPage })));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 15_000,
      refetchOnWindowFocus: true,
    },
    mutations: { retry: 0 },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <LiveTelemetryProvider>
          <AppShell>
            <Suspense fallback={<LoadingState label="Opening operator workspace" />}>
              <Routes>
                <Route path="/" element={<OverviewPage />} />
                <Route path="/devices" element={<DevicesPage />} />
                <Route path="/devices/:deviceId" element={<DeviceDetailPage />} />
                <Route path="/alerts" element={<AlertsPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </Suspense>
          </AppShell>
        </LiveTelemetryProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
