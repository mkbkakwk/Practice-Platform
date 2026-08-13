import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, type ContestDetail as ContestDetailModel, type ContestSummary } from "@/lib/api";
import ContestList from "./ContestList";
import ContestDetail from "./ContestDetail";
import ContestManage from "./ContestManage";

const auth = vi.hoisted(() => ({
  user: { id: 10, username: "student", role: "USER" as const } as
    | { id: number; username: string; role: "USER" | "TEACHER" | "ADMIN" }
    | null,
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: auth.user, loading: false, logout: vi.fn() }),
}));

const baseContest: ContestSummary = {
  id: 7,
  title: "夏季编程赛",
  description: "服务端控制的比赛",
  status: "PUBLISHED",
  phase: "UPCOMING",
  accessType: "OPEN",
  ownerId: 2,
  ownerUsername: "teacher",
  startAt: "2026-09-01T01:00:00Z",
  endAt: "2026-09-01T03:00:00Z",
  participant: false,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

function detail(overrides: Partial<ContestSummary> = {}, withProblem = false): ContestDetailModel {
  return {
    contest: { ...baseContest, ...overrides },
    problems: withProblem ? [{
      contestProblemId: 71,
      problemType: "ALGORITHM",
      problemId: 4,
      displayOrder: 1,
      label: "A",
      title: "A + B",
      difficulty: "EASY",
      slug: "a-plus-b",
      content: { description: "请计算两个整数之和。" },
    }] : [],
  };
}

function renderDetail(value: ContestDetailModel) {
  vi.spyOn(api, "getContest").mockResolvedValue({ detail: value });
  vi.spyOn(api, "getLanguages").mockResolvedValue({ languages: [{ id: "python", name: "Python", ext: ".py", template: "" }] });
  return render(<MemoryRouter initialEntries={["/contests/7"]}><Routes><Route path="/contests/:id" element={<ContestDetail />} /></Routes></MemoryRouter>);
}

describe("contest stage UI", () => {
  beforeEach(() => {
    auth.user = { id: 10, username: "student", role: "USER" };
  });

  it("renders only the server-returned student contest list and its server phase", async () => {
    vi.spyOn(api, "listContests").mockResolvedValue({ total: 1, page: 1, pageSize: 20, contests: [baseContest] });
    render(<MemoryRouter><ContestList /></MemoryRouter>);
    expect(await screen.findByText("夏季编程赛")).toBeInTheDocument();
    expect(screen.getByText("即将开始")).toBeInTheDocument();
    expect(screen.queryByText("草稿比赛")).not.toBeInTheDocument();
  });

  it("lets a student join an upcoming OPEN contest", async () => {
    const user = userEvent.setup();
    renderDetail(detail());
    const join = vi.spyOn(api, "joinContest").mockResolvedValue({ participant: { id: 1, userId: 10, username: "student", addedBy: 10, joinedAt: "2026-08-01T00:00:00Z" } });
    await user.click(await screen.findByRole("button", { name: "加入比赛" }));
    expect(join).toHaveBeenCalledWith(7);
  });

  it("shows invite-only restrictions and does not expose a join action", async () => {
    renderDetail(detail({ accessType: "INVITE_ONLY" }));
    expect(await screen.findByText("邀请制比赛仅对受邀学生开放。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "加入比赛" })).not.toBeInTheDocument();
  });

  it("uses RUNNING from the server to expose the contest submission flow", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, true));
    const submit = vi.spyOn(api, "submitContestAlgorithm").mockResolvedValue({ submissionId: 99, status: "PENDING", message: "queued" });
    vi.spyOn(api, "pollSubmission").mockResolvedValue({ id: 99, verdict: "AC", timeMs: 3, memoryKb: 1024, passed: 1, total: 1, language: "python", code: "print(1)", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 });
    await user.type(await screen.findByLabelText("A 源代码"), "print(1)");
    await user.click(screen.getByRole("button", { name: "提交代码" }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith(7, 71, "python", "print(1)"));
    expect(await screen.findByText("通过")).toBeInTheDocument();
  });

  it("keeps ended history visible while hiding submission actions", async () => {
    renderDetail(detail({ phase: "ENDED", participant: true }, true));
    expect(await screen.findByText("比赛已结束；历史题目仍可查看。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "提交代码" })).not.toBeInTheDocument();
  });

  it("shows server submission errors instead of trusting the client phase", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, true));
    vi.spyOn(api, "submitContestAlgorithm").mockRejectedValue(new Error("CONTEST_ENDED"));
    await user.type(await screen.findByLabelText("A 源代码"), "print(1)");
    await user.click(screen.getByRole("button", { name: "提交代码" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("提交失败");
  });

  it("freezes teacher management controls for RUNNING contests", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    vi.spyOn(api, "getContest").mockResolvedValue({ detail: detail({ phase: "RUNNING", participant: false }, true) });
    vi.spyOn(api, "listContestParticipants").mockResolvedValue({ total: 0, page: 1, pageSize: 50, participants: [] });
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    expect(await screen.findByLabelText("标题")).toBeDisabled();
    expect(screen.getByRole("button", { name: "保存设置" })).toBeDisabled();
    expect(screen.getByLabelText("题目 ID")).toBeDisabled();
  });
});
