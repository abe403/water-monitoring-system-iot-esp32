import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AlertLedger } from "./AlertLedger";

describe("AlertLedger", () => {
  it("renders an explicit all-clear state", () => {
    render(<MemoryRouter><AlertLedger alerts={[]} /></MemoryRouter>);
    expect(screen.getByText("No active alerts")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /review alert workspace/i })).toHaveAttribute("href", "/alerts");
  });

  it("links an active alert to its filtered workspace", () => {
    render(<MemoryRouter><AlertLedger alerts={[{
      id: "a1", deviceId: "tank-01", anomalyType: "LOW_LEVEL", state: "OPEN", score: 0.9,
      openedAt: "2026-08-25T00:00:00Z", updatedAt: "2026-08-25T00:00:00Z", resolvedAt: null, resolvedBy: null,
    }]} /></MemoryRouter>);
    expect(screen.getByText("Low Level")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /low level/i })).toHaveAttribute("href", "/alerts?device=tank-01");
  });
});
