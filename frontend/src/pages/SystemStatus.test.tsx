import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SystemStatus from "./SystemStatus";
import { api } from "@/lib/api";

describe("Admin System Status", () => {
  it("renders a read-only degraded operational snapshot", async () => {
    vi.spyOn(api, "getSystemStatus").mockResolvedValue({
      checkedAt: "2026-08-29T00:00:00Z",
      version: { gitSha: "a".repeat(40), version: "staging", buildTime: "2026-08-29T00:00:00Z", flywayVersion: "9" },
      components: {
        backend: { status: "UP", latencyMs: 2 },
        runner: { status: "DOWN", latencyMs: 750 },
      },
      queues: { main: 0, retry: 0, dlq: 0 },
      outbox: { status: "UP", latencyMs: 3, nonterminal: 0, publisherRunning: false, lastFailure: "NONE" },
      metrics: { httpRequests: 3 },
    });
    render(<SystemStatus />);
    await waitFor(() => expect(screen.getByText("系统状态")).toBeInTheDocument());
    expect(screen.getByText("只读运行证据；不会执行重启、清理或队列变更。")).toBeInTheDocument();
    expect(screen.getByText("DOWN")).toBeInTheDocument();
    expect(screen.getByText("Outbox 非终态")).toBeInTheDocument();
    expect(screen.queryByText("重启")).not.toBeInTheDocument();
  });
});
