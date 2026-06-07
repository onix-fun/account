import { onCLS, onFCP, onINP, onLCP, onTTFB, type Metric } from "web-vitals";

type TelemetryEvent = {
  type: "error" | "web_vital";
  route: string;
  name: string;
  value?: number;
};

const send = (event: TelemetryEvent) => {
  const body = JSON.stringify({ events: [event] });
  if (navigator.sendBeacon) {
    navigator.sendBeacon("/api/telemetry/frontend", new Blob([body], { type: "application/json" }));
    return;
  }
  void fetch("/api/telemetry/frontend", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true,
  });
};

const route = () => window.location.pathname;

export function installFrontendTelemetry(): void {
  window.addEventListener("error", (event) => {
    send({ type: "error", route: route(), name: event.error?.name || "Error" });
  });
  window.addEventListener("unhandledrejection", (event) => {
    const name = event.reason instanceof Error ? event.reason.name : "UnhandledRejection";
    send({ type: "error", route: route(), name });
  });
  const report = (metric: Metric) =>
    send({ type: "web_vital", route: route(), name: metric.name, value: metric.value });
  onCLS(report);
  onFCP(report);
  onINP(report);
  onLCP(report);
  onTTFB(report);
}
