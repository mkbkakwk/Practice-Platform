import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, type PublicUser, type Verdict } from "@/lib/api";
import ProblemList from "./ProblemList";
import Submissions from "./Submissions";
import OfficePractice from "./OfficePractice";
import Register from "./Register";

const auth = vi.hoisted(() => ({ user: { id: 10, username: "fixture", role: "USER" } as PublicUser | null, register: vi.fn() }));
vi.mock("@/lib/auth", () => ({ useAuth: () => auth }));
const page = (element: React.ReactNode) => render(<MemoryRouter>{element}</MemoryRouter>);

describe("visual rollout retains existing interactions", () => {
  beforeEach(() => { auth.user = { id: 10, username: "fixture", role: "USER" }; auth.register.mockReset(); });
  afterEach(() => vi.restoreAllMocks());

  it("preserves pagination and resets page on difficulty change with an accessible selected state", async () => {
    const list = vi.spyOn(api, "listProblems").mockResolvedValue({ problems: [], total: 41, page: 1, pageSize: 20 });
    const user = userEvent.setup();
    page(<ProblemList />);
    await screen.findByText("暂无题目");
    expect(screen.getByRole("region", { name: "训练题库表格" })).toHaveAttribute("tabindex", "0");
    await user.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith({ page: 2, pageSize: 20, difficulty: undefined }));
    await user.click(screen.getByRole("button", { name: "困难" }));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20, difficulty: "HARD" }));
    expect(screen.getByRole("button", { name: "困难" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
  });

  it("uses personal submissions for a student and preserves every supplied verdict without score inference", async () => {
    const verdicts: Verdict[] = ["AC", "WA", "TLE", "RE", "CE", "PENDING", "JUDGING", "SE"];
    vi.spyOn(api, "mySubmissions").mockResolvedValue({ total: 8, page: 1, pageSize: 20, submissions: verdicts.map((verdict, i) => ({ id: i + 1, verdict, timeMs: 12, memoryKb: 256, passed: 0, total: 1, language: "python", code: "", createdAt: "2026-09-01T00:00:00Z" })) });
    const all = vi.spyOn(api, "listSubmissions");
    page(<Submissions />);
    await screen.findByTitle("AC");
    for (const verdict of verdicts) expect(screen.getByTitle(verdict)).toBeInTheDocument();
    expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(9);
    expect(screen.queryByRole("button", { name: "全站" })).not.toBeInTheDocument();
    expect(all).not.toHaveBeenCalled();
  });

  it("keeps Office selection, supported submit payload, feedback and post-submit lock", async () => {
    vi.spyOn(api, "getOfficeQuestion").mockResolvedValue({ question: { id: 7, appType: "WORD", category: "基础", difficulty: "EASY", questionType: "SINGLE_CHOICE", content: "选择正确操作", options: ["保存文档", "关闭电源"], contentVisibility: "PUBLIC", createdBy: null, creatorUsername: null, createdAt: "2026-09-01T00:00:00Z" } });
    const submit = vi.spyOn(api, "submitOfficeAnswer").mockResolvedValue({ result: { correct: true, correctAnswer: "0", explanation: "保存文档。" } });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/office/7"]}><Routes><Route path="/office/:id" element={<OfficePractice />} /></Routes></MemoryRouter>);
    const option = await screen.findByRole("button", { name: /保存文档/ });
    expect(screen.getByRole("button", { name: "提交答案" })).toBeDisabled();
    await user.click(option);
    expect(option).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "提交答案" }));
    expect(await screen.findByText("回答正确")).toBeInTheDocument();
    expect(submit).toHaveBeenCalledExactlyOnceWith(7, ["0"]);
    expect(option).toBeDisabled();
  });

  it("retains registration labels/autocomplete and announces the actual auth failure", async () => {
    auth.register.mockRejectedValue(new Error("fixture failure"));
    const user = userEvent.setup();
    page(<Register />);
    expect(screen.getByRole("heading", { name: "注册" })).toBeInTheDocument();
    expect(screen.getByLabelText("密码")).toHaveAttribute("autocomplete", "new-password");
    await user.type(screen.getByLabelText("用户名"), "fixture");
    await user.type(screen.getByLabelText("密码"), "fixture-only-password");
    await user.click(screen.getByRole("button", { name: "注册" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("注册失败");
    expect(auth.register).toHaveBeenCalledExactlyOnceWith("fixture", "fixture-only-password");
  });
});
