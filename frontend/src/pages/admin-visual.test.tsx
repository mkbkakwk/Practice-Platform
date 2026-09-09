import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, type ProblemListItem } from "@/lib/api";
import AdminProblemList from "./AdminProblemList";
import AdminUserList from "./AdminUserList";
import ProblemForm from "./ProblemForm";
import OfficeQuestionForm from "./OfficeQuestionForm";
import { AdminGuard, TeacherGuard } from "@/components/AdminGuard";

const auth = vi.hoisted(() => ({ role: "TEACHER" }));
vi.mock("@/lib/auth", () => ({ useAuth: () => ({ user: { id: 9, username: "fixture-manager", role: auth.role }, loading: false }) }));
const problem: ProblemListItem = {
  id: 81, slug: "fixture-sum", title: "隔离题目", difficulty: "EASY", tags: [],
  timeLimit: 1000, memoryLimit: 256, visible: true, contentVisibility: "PUBLIC",
  createdBy: 9, creatorUsername: "fixture-manager", submissionCount: 2, createdAt: "2026-09-01T00:00:00Z",
};

describe("management presentation preserves safe operations", () => {
  beforeEach(() => { auth.role = "TEACHER"; });

  it("keeps all problem columns in a named keyboard-scrollable region", async () => {
    vi.spyOn(api, "listManageProblems").mockResolvedValue({ problems: [problem], total: 1, page: 1, pageSize: 50 });
    render(<MemoryRouter><AdminProblemList /></MemoryRouter>);
    expect(await screen.findByText(problem.title)).toBeInTheDocument();
    const region = screen.getByRole("region", { name: "算法题管理" });
    expect(region).toHaveAttribute("tabindex", "0");
    for (const name of ["ID", "题目 / 创建信息", "难度", "状态", "操作"]) {
      expect(within(region).getByRole("columnheader", { name })).toBeInTheDocument();
    }
    expect(within(region).getByText("提交：2")).toBeInTheDocument();
  });

  it("still blocks teacher permanent deletion of submitted work", async () => {
    vi.spyOn(api, "listManageProblems").mockResolvedValue({ problems: [problem], total: 1, page: 1, pageSize: 50 });
    const remove = vi.spyOn(api, "deleteProblem");
    const alert = vi.spyOn(window, "alert").mockImplementation(() => undefined);
    render(<MemoryRouter><AdminProblemList /></MemoryRouter>);
    await screen.findByText(problem.title);
    await userEvent.click(screen.getByRole("button", { name: "彻底删除" }));
    expect(alert).toHaveBeenCalledWith("该内容已有学生提交，只能停用，不能彻底删除。");
    expect(remove).not.toHaveBeenCalled();
  });

  it("retains Admin destructive confirmation and cancellation", async () => {
    auth.role = "ADMIN";
    vi.spyOn(api, "listManageProblems").mockResolvedValue({ problems: [problem], total: 1, page: 1, pageSize: 50 });
    const remove = vi.spyOn(api, "deleteProblem");
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<MemoryRouter><AdminProblemList /></MemoryRouter>);
    await screen.findByText(problem.title);
    await userEvent.click(screen.getByRole("button", { name: "彻底删除" }));
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining("无法恢复"));
    expect(remove).not.toHaveBeenCalled();
  });

  it("identifies the target account on each role action and preserves the API payload", async () => {
    const user = { id: 12, username: "fixture-student", role: "USER" as const, solvedCount: 1, createdAt: "2026-09-01T00:00:00Z" };
    vi.spyOn(api, "listUsers").mockResolvedValue({ users: [user], total: 1, page: 1, pageSize: 100 });
    const change = vi.spyOn(api, "updateUserRole").mockResolvedValue({ user: { ...user, role: "TEACHER" } });
    render(<MemoryRouter><AdminUserList /></MemoryRouter>);
    const teacher = await screen.findByRole("button", { name: "将 fixture-student 设为老师" });
    expect(screen.getByRole("button", { name: "将 fixture-student 设为学生" })).toBeDisabled();
    await userEvent.click(teacher);
    expect(change).toHaveBeenCalledExactlyOnceWith(12, "TEACHER");
    expect(teacher).toHaveAttribute("aria-pressed", "true");
  });

  it("labels problem inputs without relaxing existing validation", async () => {
    const create = vi.spyOn(api, "createProblem");
    render(<MemoryRouter><ProblemForm mode="create" /></MemoryRouter>);
    expect(screen.getByLabelText("标题")).toBeInTheDocument();
    expect(screen.getByLabelText("时间限制 (ms)")).toHaveAttribute("min", "100");
    expect(screen.getByLabelText("内存限制 (MB)")).toHaveAttribute("max", "1024");
    await userEvent.click(screen.getByRole("button", { name: "保存题目" }));
    expect(screen.getByText("slug、标题、题面描述不能为空")).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });

  it("exposes Office visibility and option controls accessibly without saving on toggle", async () => {
    const create = vi.spyOn(api, "createOfficeQuestion");
    render(<MemoryRouter><OfficeQuestionForm mode="create" /></MemoryRouter>);
    expect(screen.getByLabelText("题目内容")).toBeInTheDocument();
    const visibility = screen.getByRole("switch", { name: "是否可见" });
    expect(visibility).toHaveAttribute("aria-checked", "true");
    await userEvent.click(visibility);
    expect(visibility).toHaveAttribute("aria-checked", "false");
    expect(screen.getByRole("button", { name: "删除选项 A" })).toBeDisabled();
    expect(create).not.toHaveBeenCalled();
  });

  it("does not relax Teacher/Admin route guards", () => {
    auth.role = "USER";
    render(<MemoryRouter><AdminGuard><div>private-admin</div></AdminGuard><TeacherGuard><div>private-teacher</div></TeacherGuard></MemoryRouter>);
    expect(screen.queryByText("private-admin")).not.toBeInTheDocument();
    expect(screen.queryByText("private-teacher")).not.toBeInTheDocument();
  });
});
