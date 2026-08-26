import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Reading } from "../lib/types";
import { TankGauge } from "./TankGauge";

const reading: Reading = {
  deviceId: "tank-01",
  bootId: 2,
  seq: 10,
  observedAt: new Date().toISOString(),
  receivedAt: new Date().toISOString(),
  distanceMm: 350,
  tempTenths: 241,
  rssiDbm: -63,
  levelPct: 67.4,
  quality: "GOOD",
};

describe("TankGauge", () => {
  it("exposes the calibrated level and quality without relying on color", () => {
    render(<TankGauge reading={reading} deviceId="tank-01" />);
    expect(screen.getByRole("heading", { name: "tank-01" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Tank level 67.4%" })).toBeInTheDocument();
    expect(screen.getByText("GOOD")).toBeInTheDocument();
    expect(screen.getByText("24.1°C")).toBeInTheDocument();
  });

  it("renders an honest awaiting state without a reading", () => {
    render(<TankGauge deviceId="tank-01" />);
    expect(screen.getByRole("img", { name: "No calibrated tank level available" })).toBeInTheDocument();
    expect(screen.getByText("awaiting reading")).toBeInTheDocument();
  });
});
