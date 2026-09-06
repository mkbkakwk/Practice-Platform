import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, type ContestStanding, type PublicUser } from "@/lib/api";
import { VerdictBadge, VERDICT_LABEL } from "@/lib/verdict";
import { ContestPhaseBadge } from "@/components/contest/ContestVisuals";
import { Button } from "@/components/ui/button";
import ContestStandings from "./ContestStandings";

const auth = vi.hoisted(() => ({ user: { id: 10, username: "same-name", role: "USER" } as PublicUser | null }));
vi.mock("@/lib/auth", () => ({ useAuth: () => ({ user: auth.user }) }));

function standings(): ContestStanding {
  return {
    contestId: 7, phase: "RUNNING", scoringMode: "ICPC", frozen: false, managerView: false,
    freezeAt: null, generatedAt: "2026-09-01T02:00:00Z",
    entries: [
      { rank: 3, userId: 20, username: "same-name", totalScore: 0, solved: 1, penaltyMinutes: 42,
        problems: [{ contestProblemId: 71, label: "A", score: 100, solved: true, attempts: 2, penaltyMinutes: 42 }] },
      { rank: 8, userId: 10, username: "same-name", totalScore: 0, solved: 0, penaltyMinutes: 0,
        problems: [{ contestProblemId: 71, label: "A", score: null, solved: false, attempts: 3, penaltyMinutes: null }] },
    ],
  };
}

function renderPage() {
  return render(<MemoryRouter initialEntries={["/contests/7/standings"]}><Routes><Route path="/contests/:id/standings" element={<ContestStandings />} /></Routes></MemoryRouter>);
}

describe("graphite standings presentation preserves the server contract", () => {
  beforeEach(() => { auth.user = { id: 10, username: "same-name", role: "USER" }; });
  afterEach(() => vi.restoreAllMocks());

  it("highlights by user ID, preserves supplied order/rank and does not label unresolved attempts WA", async () => {
    vi.spyOn(api, "getContestStandings").mockResolvedValue({ standings: standings() });
    renderPage();
    const table = await screen.findByRole("table");
    const rows = within(table).getAllByRole("row").slice(1);
    expect(within(rows[0]).getByText("3")).toBeInTheDocument();
    expect(rows[0]).not.toHaveAttribute("data-current-user");
    expect(rows[1]).toHaveAttribute("data-current-user", "true");
    expect(within(rows[1]).getByText("我")).toBeInTheDocument();
    expect(within(rows[1]).getByText("8")).toBeInTheDocument();
    expect(within(rows[0]).getByText("AC · 42分")).toBeInTheDocument();
    expect(within(rows[1]).getByText("3 次")).toBeInTheDocument();
    expect(within(table).queryByText(/WA/)).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "比赛排名表格" })).toHaveAttribute("tabindex", "0");
  });

  it("does not invent a current-user row for anonymous viewers", async () => {
    auth.user = null;
    vi.spyOn(api, "getContestStandings").mockResolvedValue({ standings: standings() });
    renderPage();
    await screen.findByRole("table");
    expect(screen.queryByText("我")).not.toBeInTheDocument();
  });

  it("retains SCORE numbers without converting partial scores into AC", async () => {
    const value = standings();
    value.scoringMode = "SCORE";
    value.entries = [{ ...value.entries[0], totalScore: 37.5, problems: [{ ...value.entries[0].problems[0], score: 37.5, solved: false }] }];
    vi.spyOn(api, "getContestStandings").mockResolvedValue({ standings: value });
    renderPage();
    await screen.findByRole("table");
    expect(screen.getAllByText("37.5")).toHaveLength(2);
    expect(screen.queryByRole("columnheader", { name: "罚时" })).not.toBeInTheDocument();
    expect(screen.queryByText(/AC/)).not.toBeInTheDocument();
  });

  it("announces loading and an empty server result without sample rows", async () => {
    let finish!: (value: { standings: ContestStanding }) => void;
    vi.spyOn(api, "getContestStandings").mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    renderPage();
    expect(screen.getByRole("status", { name: "加载比赛排名" })).toHaveAttribute("aria-busy", "true");
    finish({ standings: { ...standings(), entries: [] } });
    expect(await screen.findByText("暂无参赛者。")).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(1);
  });

  it("preserves authorization failures as alerts instead of falling back to fake standings", async () => {
    vi.spyOn(api, "getContestStandings").mockRejectedValue(new ApiError(403, "无权查看比赛排名"));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("无权查看比赛排名");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});

describe("status information is not color-only", () => {
  it("keeps destructive surfaces opaque in dark mode and preserves disabled behavior", () => {
    render(<Button variant="destructive" disabled>测试操作</Button>);
    const button = screen.getByRole("button", { name: "测试操作" });
    expect(button).toBeDisabled();
    expect(button).toHaveClass("text-destructive-foreground", "dark:hover:bg-destructive");
    expect(button).not.toHaveClass("dark:bg-destructive/60");
  });

  it("retains a readable label and explicit verdict identity for every verdict", () => {
    render(<>{Object.keys(VERDICT_LABEL).map((key) => <VerdictBadge key={key} verdict={key as keyof typeof VERDICT_LABEL} />)}</>);
    for (const [verdict, label] of Object.entries(VERDICT_LABEL)) {
      expect(screen.getByTitle(verdict)).toHaveTextContent(label);
    }
  });

  it("renders all server phases as text; only RUNNING has a decorative pulse", () => {
    const { container } = render(<>{(["DRAFT", "UPCOMING", "RUNNING", "ENDED", "CANCELLED"] as const).map((phase) => <ContestPhaseBadge key={phase} phase={phase} />)}</>);
    for (const label of ["草稿", "即将开始", "进行中", "已结束", "已取消"]) expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelectorAll(".pilot-running-dot")).toHaveLength(1);
    expect(container.querySelector(".pilot-running-dot")).toHaveAttribute("aria-hidden", "true");
  });
});
