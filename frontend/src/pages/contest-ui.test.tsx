import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  api,
  type ContestDetail as ContestDetailModel,
  type ContestProblemItem,
  type ContestStanding,
  type ContestSummary,
  type LanguageDef,
} from "@/lib/api";
import ContestList from "./ContestList";
import ContestDetail, { CONTEST_REFRESH_MS } from "./ContestDetail";
import ContestManage from "./ContestManage";
import ContestStandings from "./ContestStandings";

const auth = vi.hoisted(() => ({
  user: { id: 10, username: "student", role: "USER" as const } as
    | { id: number; username: string; role: "USER" | "TEACHER" | "ADMIN" }
    | null,
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: auth.user, loading: false, logout: vi.fn() }),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), info: vi.fn(), error: vi.fn() },
}));

vi.mock("@/components/CodeEditor", () => ({
  CodeEditor: ({ value, onChange, ariaLabel }: { value: string; onChange: (value: string) => void; ariaLabel: string }) => (
    <textarea aria-label={ariaLabel} value={value} onChange={(event) => onChange(event.target.value)} />
  ),
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
  scoringMode: "SCORE",
  startAt: "2026-09-01T01:00:00Z",
  endAt: "2026-09-01T03:00:00Z",
  freezeAt: null,
  participant: false,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const problems: ContestProblemItem[] = [
  {
    contestProblemId: 71,
    problemType: "ALGORITHM",
    problemId: 4,
    displayOrder: 1,
    label: "A",
    title: "A + B",
    difficulty: "EASY",
    slug: "a-plus-b",
    content: {
      description: "A 题正文",
      inputFmt: "输入两个整数",
      outputFmt: "输出它们的和",
      samples: [{ input: "1 2", output: "3" }, { input: "5 8", output: "13" }],
    },
  },
  {
    contestProblemId: 72,
    problemType: "ALGORITHM",
    problemId: 5,
    displayOrder: 2,
    label: "B",
    title: "求和",
    difficulty: "EASY",
    slug: "sum-n",
    content: { description: "B 题正文" },
  },
  {
    contestProblemId: 73,
    problemType: "OFFICE_DOCX",
    problemId: 6,
    displayOrder: 3,
    label: "C",
    title: "Word 排版",
    difficulty: "MEDIUM",
    slug: null,
    content: { description: "C 题正文", hasStarter: true, starterDocName: "contest-starter.docx", hasReference: true },
  },
];

const choiceProblem: ContestProblemItem = {
  contestProblemId: 74,
  problemType: "OFFICE_CHOICE",
  problemId: 60,
  displayOrder: 4,
  label: "D",
  title: "Word 快捷键",
  difficulty: "EASY",
  slug: null,
  content: {
    appType: "WORD",
    category: "基础",
    difficulty: "EASY",
    questionType: "SINGLE_CHOICE",
    content: "保存文档的快捷键是？",
    options: ["Ctrl+P", "Ctrl+S"],
  },
};

const multiChoiceProblem: ContestProblemItem = {
  contestProblemId: 75,
  problemType: "OFFICE_CHOICE",
  problemId: 61,
  displayOrder: 5,
  label: "E",
  title: "Excel 多选",
  difficulty: "MEDIUM",
  slug: null,
  content: {
    appType: "EXCEL",
    category: "公式",
    difficulty: "MEDIUM",
    questionType: "MULTI_CHOICE",
    content: "哪些属于 Excel 函数？",
    options: ["SUM", "AVERAGE", "SLIDE"],
  },
};

const trueFalseProblem: ContestProblemItem = {
  contestProblemId: 76,
  problemType: "OFFICE_CHOICE",
  problemId: 62,
  displayOrder: 6,
  label: "F",
  title: "Word 判断",
  difficulty: "EASY",
  slug: null,
  content: {
    appType: "WORD",
    category: "基础",
    difficulty: "EASY",
    questionType: "TRUE_FALSE",
    content: "Word 可以保存 DOCX 文档。",
    options: ["正确", "错误"],
  },
};

const defaultLanguages: LanguageDef[] = [
  { id: "javascript", name: "JavaScript", ext: ".js", template: "console.log('js')" },
  { id: "java", name: "Java", ext: ".java", template: "class Main {}" },
];

function detail(overrides: Partial<ContestSummary> = {}, problemItems: ContestProblemItem[] = []): ContestDetailModel {
  return { contest: { ...baseContest, ...overrides }, problems: problemItems };
}

function renderDetail(value: ContestDetailModel, languages: LanguageDef[] = defaultLanguages, entry = "/contests/7") {
  vi.spyOn(api, "getContest").mockResolvedValue({ detail: value });
  vi.spyOn(api, "getLanguages").mockResolvedValue({ languages });
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes><Route path="/contests/:id" element={<ContestDetail />} /></Routes>
    </MemoryRouter>,
  );
}

function mockManage(value: ContestDetailModel, participantTotal = 0) {
  vi.spyOn(api, "getContest").mockResolvedValue({ detail: value });
  vi.spyOn(api, "listContestParticipants").mockImplementation(async (_id, options) => ({
    total: participantTotal,
    page: options?.page ?? 1,
    pageSize: options?.pageSize ?? 20,
    participants: participantTotal > 0 ? [{ id: options?.page ?? 1, userId: 100 + (options?.page ?? 1), username: `student-${options?.page ?? 1}`, addedBy: 2, joinedAt: "2026-08-01T00:00:00Z" }] : [],
  }));
  vi.spyOn(api, "listManageProblems").mockResolvedValue({ total: 1, page: 1, pageSize: 20, problems: [{ id: 44, title: "最大子数组", slug: "max-subarray", difficulty: "MEDIUM", contentVisibility: "PUBLIC", visible: true, tags: [], createdBy: 2, creatorUsername: "teacher", createdAt: "2026-08-01T00:00:00Z", timeLimit: 1000, memoryLimit: 128, submissionCount: 0 }] });
  vi.spyOn(api, "listManageOfficeQuestions").mockResolvedValue({ total: 0, page: 1, pageSize: 20, questions: [] });
  vi.spyOn(api, "listManageDocExercises").mockResolvedValue({ total: 0, page: 1, pageSize: 20, exercises: [] });
  vi.spyOn(api, "searchContestStudents").mockResolvedValue({ total: 1, page: 1, pageSize: 10, students: [{ id: 10, username: "stage_student", role: "USER" }] });
  vi.spyOn(api, "listRejudgeableContestSubmissions").mockResolvedValue({ total: 0, page: 1, pageSize: 20, submissions: [] });
}

describe("contest stage UI", () => {
  beforeEach(() => {
    auth.user = { id: 10, username: "student", role: "USER" };
  });

  afterEach(() => vi.useRealTimers());

  it("renders the server contest list with distinct phase and time semantics", async () => {
    vi.spyOn(api, "listContests").mockResolvedValue({ total: 1, page: 1, pageSize: 20, contests: [baseContest] });
    render(<MemoryRouter><ContestList /></MemoryRouter>);
    expect(await screen.findByText("夏季编程赛")).toBeInTheDocument();
    expect(screen.getByText("即将开始")).toHaveClass("bg-blue-100");
    expect(screen.getByText(/\d+\/\d+ \d+:\d+ 开始$/)).toBeInTheDocument();
  });

  it("lets a student join an upcoming OPEN contest once", async () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-08-31T12:00:00Z"));
    const user = userEvent.setup();
    vi.spyOn(api, "getContest")
      .mockResolvedValueOnce({ detail: detail() })
      .mockResolvedValue({ detail: detail({ participant: true }) });
    vi.spyOn(api, "getLanguages").mockResolvedValue({ languages: defaultLanguages });
    render(<MemoryRouter initialEntries={["/contests/7"]}><Routes><Route path="/contests/:id" element={<ContestDetail />} /></Routes></MemoryRouter>);
    const join = vi.spyOn(api, "joinContest").mockResolvedValue({ participant: { id: 1, userId: 10, username: "student", addedBy: 10, joinedAt: "2026-08-01T00:00:00Z" } });
    const button = await screen.findByRole("button", { name: "加入比赛" });
    await user.dblClick(button);
    expect(join).toHaveBeenCalledTimes(1);
  });

  it("uses the first server language even when Python is unavailable", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[0]]));
    const submit = vi.spyOn(api, "submitContestAlgorithm").mockResolvedValue({ submissionId: 99, status: "PENDING", message: "queued" });
    vi.spyOn(api, "pollSubmission").mockResolvedValue({ id: 99, verdict: "AC", timeMs: 3, memoryKb: 1024, passed: 2, total: 2, language: "javascript", code: "alert(1)", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 });
    const editor = await screen.findByLabelText("A 源代码");
    expect(editor).toHaveValue("console.log('js')");
    await user.clear(editor);
    await user.type(editor, "alert(1)");
    await user.click(screen.getByRole("button", { name: "提交代码" }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith(7, 71, "javascript", "alert(1)"));
    expect(await screen.findByText("通过")).toBeInTheDocument();
  });

  it("prevents a synchronous double click from creating two algorithm submissions", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[0]]));
    let resolveSubmit!: (value: Awaited<ReturnType<typeof api.submitContestAlgorithm>>) => void;
    const submit = vi.spyOn(api, "submitContestAlgorithm").mockReturnValue(new Promise((resolve) => { resolveSubmit = resolve; }));
    vi.spyOn(api, "pollSubmission").mockResolvedValue({ id: 120, verdict: "AC", timeMs: 3, memoryKb: 1024, passed: 2, total: 2, language: "javascript", code: "console.log('js')", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 });
    const button = await screen.findByRole("button", { name: "提交代码" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(submit).toHaveBeenCalledTimes(1);
    await act(async () => resolveSubmit({ submissionId: 120, status: "PENDING", message: "queued" }));
    expect(await screen.findByText("通过")).toBeInTheDocument();
  });

  it("disables algorithm submission when the server exposes no languages", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[0]]), []);
    expect(await screen.findByRole("alert")).toHaveTextContent("没有可用编程语言");
    expect(screen.getByRole("button", { name: "提交代码" })).toBeDisabled();
  });

  it("preserves per-language and per-problem drafts while navigating A/B/C", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, problems));
    const aEditor = await screen.findByLabelText("A 源代码");
    await user.clear(aEditor);
    await user.type(aEditor, "A javascript draft");

    await user.click(screen.getByLabelText("A 编程语言"));
    await user.click(await screen.findByRole("option", { name: "Java" }));
    expect(screen.getByLabelText("A 源代码")).toHaveValue("class Main {}");
    await user.clear(screen.getByLabelText("A 源代码"));
    await user.type(screen.getByLabelText("A 源代码"), "A java draft");
    await user.click(screen.getByLabelText("A 编程语言"));
    await user.click(await screen.findByRole("option", { name: "JavaScript" }));
    expect(screen.getByLabelText("A 源代码")).toHaveValue("A javascript draft");

    await user.click(screen.getAllByRole("button", { name: /B求和算法/ })[0]);
    expect(await screen.findByText("B 题正文")).toBeInTheDocument();
    expect(screen.queryByText("A 题正文")).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText("B 源代码"));
    await user.type(screen.getByLabelText("B 源代码"), "B draft");
    await user.click(screen.getAllByRole("button", { name: /CWord 排版DOCX/ })[0]);
    expect(await screen.findByText("C 题正文")).toBeInTheDocument();
    expect(screen.getByLabelText("DOCX 文件")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /AA \+ B算法/ })[0]);
    expect(screen.getByLabelText("A 源代码")).toHaveValue("A javascript draft");
  });

  it("restores the selected problem from the URL and renders every sample pair", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, problems), defaultLanguages, "/contests/7?problem=71");
    expect(await screen.findByText("样例 1")).toBeInTheDocument();
    expect(screen.getByText("样例 2")).toBeInTheDocument();
    expect(screen.getByText("1 2")).toBeInTheDocument();
    expect(screen.getByText("13")).toBeInTheDocument();
    expect(screen.queryByText("B 题正文")).not.toBeInTheDocument();
  });

  it("shows a non-failure notice when polling times out with a pending submission", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[0]]));
    vi.spyOn(api, "submitContestAlgorithm").mockResolvedValue({ submissionId: 100, status: "PENDING", message: "queued" });
    vi.spyOn(api, "pollSubmission").mockResolvedValue({ id: 100, verdict: "JUDGING", timeMs: 0, memoryKb: 0, passed: 0, total: 0, language: "javascript", code: "x", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 });
    const editor = await screen.findByLabelText("A 源代码");
    await user.clear(editor);
    await user.type(editor, "x");
    await user.click(screen.getByRole("button", { name: "提交代码" }));
    expect(await screen.findByText("判题仍在进行，可前往提交记录查看最终结果。")).toBeInTheDocument();
    expect(screen.queryByText("提交失败")).not.toBeInTheDocument();
  });

  it("keeps a completed A result bound to A after switching to B", async () => {
    const user = userEvent.setup();
    let settle!: (value: Awaited<ReturnType<typeof api.pollSubmission>>) => void;
    const pending = new Promise<Awaited<ReturnType<typeof api.pollSubmission>>>((resolve) => { settle = resolve; });
    renderDetail(detail({ phase: "RUNNING", participant: true }, problems.slice(0, 2)));
    vi.spyOn(api, "submitContestAlgorithm").mockResolvedValue({ submissionId: 101, status: "PENDING", message: "queued" });
    vi.spyOn(api, "pollSubmission").mockReturnValue(pending);
    const editor = await screen.findByLabelText("A 源代码");
    await user.clear(editor);
    await user.type(editor, "x");
    await user.click(screen.getByRole("button", { name: "提交代码" }));
    await user.click(screen.getAllByRole("button", { name: /B求和算法/ })[0]);
    await act(async () => settle({ id: 101, verdict: "AC", timeMs: 4, memoryKb: 512, passed: 2, total: 2, language: "javascript", code: "x", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 }));
    expect(screen.getByText("B 题正文")).toBeInTheDocument();
    expect(screen.queryByText("通过")).not.toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /AA \+ B算法/ })[0]);
    expect(await screen.findByText("通过")).toBeInTheDocument();
  });

  it("ignores a late tick from an older submission after a newer submission completes", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[0]]));
    vi.spyOn(api, "submitContestAlgorithm")
      .mockResolvedValueOnce({ submissionId: 130, status: "PENDING", message: "queued" })
      .mockResolvedValueOnce({ submissionId: 131, status: "PENDING", message: "queued" });
    let oldTick: ((poll: number, submission: Awaited<ReturnType<typeof api.pollSubmission>> | null) => void) | undefined;
    vi.spyOn(api, "pollSubmission")
      .mockImplementationOnce(async (_id, options) => {
        oldTick = options?.onTick;
        return { id: 130, verdict: "JUDGING", timeMs: 0, memoryKb: 0, passed: 0, total: 2, language: "javascript", code: "old", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 };
      })
      .mockResolvedValueOnce({ id: 131, verdict: "AC", timeMs: 4, memoryKb: 512, passed: 2, total: 2, language: "javascript", code: "new", createdAt: "2026-09-01T01:31:00Z", contestProblemId: 71 });
    await user.click(await screen.findByRole("button", { name: "提交代码" }));
    expect(await screen.findByText("判题仍在进行，可前往提交记录查看最终结果。")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "提交代码" }));
    expect(await screen.findByText("Submission #131")).toBeInTheDocument();
    act(() => oldTick?.(99, { id: 130, verdict: "WA", timeMs: 9, memoryKb: 512, passed: 1, total: 2, language: "javascript", code: "old", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 71 }));
    expect(screen.getByText("Submission #131")).toBeInTheDocument();
    expect(screen.queryByText("答案错误")).not.toBeInTheDocument();
  });

  it("aborts in-flight submission polling when the contest page unmounts", async () => {
    const user = userEvent.setup();
    const view = renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[0]]));
    vi.spyOn(api, "submitContestAlgorithm").mockResolvedValue({ submissionId: 140, status: "PENDING", message: "queued" });
    let pollingSignal: AbortSignal | undefined;
    vi.spyOn(api, "pollSubmission").mockImplementation((_id, options) => {
      pollingSignal = options?.signal;
      return new Promise((_resolve, reject) => options?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")), { once: true }));
    });
    await user.click(await screen.findByRole("button", { name: "提交代码" }));
    await waitFor(() => expect(pollingSignal).toBeDefined());
    view.unmount();
    expect(pollingSignal?.aborted).toBe(true);
  });

  it("renders structured and truncated DOCX judging details", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[2]]));
    vi.spyOn(api, "submitContestOffice").mockResolvedValue({ submission: {
      id: 201, userId: 10, exerciseId: 6, studentDocName: "answer.docx", status: "NEEDS_REVIEW", score: 56, teacherComment: null, judgeVersion: "office-docx-v1", errorCategory: null, judgedAt: "2026-09-01T01:31:00Z", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 73,
      resultDetail: { judgeVersion: "office-docx-v1", totalScore: 100, earnedScore: 56, passed: false, totalErrorCount: 14, truncated: true, items: [{ ruleId: "font", target: "第 2 段", expected: "宋体", actual: "微软雅黑", score: 10, earned: 0, passed: false, message: "字体不匹配" }] },
    } });
    const file = new File(["docx"], "answer.docx", { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
    await user.upload(await screen.findByLabelText("DOCX 文件"), file);
    expect(screen.getByText(/answer.docx/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "提交 DOCX" }));
    expect(await screen.findByText("56")).toBeInTheDocument();
    expect(screen.getByText("/ 100")).toBeInTheDocument();
    expect(screen.getByText(/第 2 段/)).toBeInTheDocument();
    expect(screen.getByText(/仅显示.*部分结果/)).toBeInTheDocument();
  });

  it("submits an Office choice inside the contest without rendering an answer or explanation", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, [choiceProblem]));
    const submit = vi.spyOn(api, "submitContestChoice").mockResolvedValue({
      submission: {
        recordId: 301,
        contestProblemId: 74,
        selected: ["1"],
        correct: true,
        createdAt: "2026-09-01T01:30:00Z",
      },
    });
    const user = userEvent.setup();
    expect(await screen.findByText("保存文档的快捷键是？")).toBeInTheDocument();
    expect(screen.queryByText(/解析|正确答案/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Ctrl+S" }));
    await user.click(screen.getByRole("button", { name: "提交答案" }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith(7, 74, ["1"]));
    expect(await screen.findByText(/回答正确/)).toBeInTheDocument();
  });

  it("supports multi-choice and true/false contest controls with canonical selections", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [multiChoiceProblem, trueFalseProblem]));
    const submit = vi.spyOn(api, "submitContestChoice")
      .mockResolvedValueOnce({ submission: { recordId: 302, contestProblemId: 75, selected: ["0", "1"], correct: true, createdAt: "2026-09-01T01:30:00Z" } })
      .mockResolvedValueOnce({ submission: { recordId: 303, contestProblemId: 76, selected: ["T"], correct: true, createdAt: "2026-09-01T01:31:00Z" } });

    await user.click(await screen.findByRole("checkbox", { name: "SUM" }));
    await user.click(screen.getByRole("checkbox", { name: "AVERAGE" }));
    await user.click(screen.getByRole("button", { name: "提交答案" }));
    await waitFor(() => expect(submit).toHaveBeenNthCalledWith(1, 7, 75, ["0", "1"]));

    await user.click(screen.getAllByRole("button", { name: /FWord 判断Office 选择题/ })[0]);
    await user.click(await screen.findByRole("radio", { name: "正确" }));
    await user.click(screen.getByRole("button", { name: "提交答案" }));
    await waitFor(() => expect(submit).toHaveBeenNthCalledWith(2, 7, 76, ["T"]));
  });

  it("navigates one active workspace across algorithm, single, multi, DOCX, and true/false", async () => {
    const user = userEvent.setup();
    const mixed: ContestProblemItem[] = [
      problems[0],
      { ...choiceProblem, label: "B", displayOrder: 2 },
      { ...multiChoiceProblem, label: "C", displayOrder: 3 },
      { ...problems[2], label: "D", displayOrder: 4 },
      { ...trueFalseProblem, label: "E", displayOrder: 5 },
    ];
    renderDetail(detail({ phase: "RUNNING", participant: true }, mixed));

    expect(await screen.findByLabelText("A 源代码")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /BWord 快捷键Office 选择题/ })[0]);
    expect(await screen.findByRole("radio", { name: "Ctrl+S" })).toBeInTheDocument();
    expect(screen.queryByLabelText("A 源代码")).not.toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /CExcel 多选Office 选择题/ })[0]);
    expect(await screen.findByRole("checkbox", { name: "SUM" })).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /DWord 排版DOCX/ })[0]);
    expect(await screen.findByLabelText("DOCX 文件")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /EWord 判断Office 选择题/ })[0]);
    expect(await screen.findByRole("radio", { name: "正确" })).toBeInTheDocument();
    expect(screen.queryByLabelText("DOCX 文件")).not.toBeInTheDocument();
  });

  it("preserves each Office choice draft and result while navigating between problems", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [choiceProblem, multiChoiceProblem]));
    vi.spyOn(api, "submitContestChoice").mockResolvedValue({
      submission: { recordId: 304, contestProblemId: 74, selected: ["1"], correct: true, createdAt: "2026-09-01T01:30:00Z" },
    });

    await user.click(await screen.findByRole("radio", { name: "Ctrl+S" }));
    await user.click(screen.getByRole("button", { name: "提交答案" }));
    expect(await screen.findByText(/Record #304/)).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /EExcel 多选Office 选择题/ })[0]);
    await user.click(await screen.findByRole("checkbox", { name: "SUM" }));
    expect(screen.queryByText(/Record #304/)).not.toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /DWord 快捷键Office 选择题/ })[0]);
    expect(screen.getByRole("radio", { name: "Ctrl+S" })).toBeChecked();
    expect(screen.getByText(/Record #304/)).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /EExcel 多选Office 选择题/ })[0]);
    expect(screen.getByRole("checkbox", { name: "SUM" })).toBeChecked();
  });

  it("prevents a synchronous double click from submitting one Office choice twice", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, [choiceProblem]));
    let resolveSubmit!: (value: Awaited<ReturnType<typeof api.submitContestChoice>>) => void;
    const submit = vi.spyOn(api, "submitContestChoice").mockReturnValue(new Promise((resolve) => { resolveSubmit = resolve; }));
    fireEvent.click(await screen.findByRole("radio", { name: "Ctrl+S" }));
    const button = screen.getByRole("button", { name: "提交答案" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(submit).toHaveBeenCalledTimes(1);
    await act(async () => resolveSubmit({ submission: { recordId: 305, contestProblemId: 74, selected: ["1"], correct: true, createdAt: "2026-09-01T01:30:00Z" } }));
    expect(await screen.findByText(/Record #305/)).toBeInTheDocument();
  });

  it("downloads the contest-gated DOCX starter from the active problem", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[2]]));
    const download = vi.spyOn(api, "downloadContestStarter").mockResolvedValue(undefined);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "下载 contest-starter.docx" }));
    expect(download).toHaveBeenCalledWith(7, 73, "contest-starter.docx");
  });

  it("keeps the contest starter unavailable before start and prevents duplicate downloads", async () => {
    const upcoming = renderDetail(detail({ phase: "UPCOMING", participant: true }, [problems[2]]));
    expect(await screen.findByText("比赛尚未开始。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "下载 contest-starter.docx" })).not.toBeInTheDocument();
    upcoming.unmount();

    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[2]]));
    let resolveDownload!: () => void;
    const download = vi.spyOn(api, "downloadContestStarter").mockReturnValue(new Promise<void>((resolve) => { resolveDownload = resolve; }));
    const button = await screen.findByRole("button", { name: "下载 contest-starter.docx" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(download).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "下载中..." })).toBeDisabled();
    await act(async () => resolveDownload());
    expect(await screen.findByRole("button", { name: "下载 contest-starter.docx" })).toBeEnabled();
  });

  it("rejects non-DOCX and oversized files before a contest upload", async () => {
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[2]]));
    const submit = vi.spyOn(api, "submitContestOffice");
    const input = await screen.findByLabelText("DOCX 文件");
    fireEvent.change(input, { target: { files: [new File(["text"], "answer.txt", { type: "text/plain" })] } });
    expect(screen.getByRole("alert")).toHaveTextContent("仅支持 DOCX");
    expect(screen.getByRole("button", { name: "提交 DOCX" })).toBeDisabled();

    const oversized = new File(["docx"], "large.docx", { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
    Object.defineProperty(oversized, "size", { value: 10 * 1024 * 1024 + 1 });
    fireEvent.change(input, { target: { files: [oversized] } });
    expect(screen.getByRole("alert")).toHaveTextContent("文件超过 10 MiB");
    expect(submit).not.toHaveBeenCalled();
  });

  it("uses a non-layout native file input and keeps a long selected DOCX name width-safe", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[2]]));
    const input = await screen.findByLabelText("DOCX 文件");
    expect(input).toHaveClass("sr-only");
    const triggerClick = vi.spyOn(input, "click");
    await user.click(screen.getByRole("button", { name: "选择 DOCX 文件" }));
    expect(triggerClick).toHaveBeenCalledTimes(1);
    triggerClick.mockRestore();

    const name = "very-long-document-name-that-must-not-widen-the-mobile-contest-workspace-stage66-answer.docx";
    const file = new File(["docx"], name, { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
    await user.upload(input, file);
    const filename = screen.getByText(name);
    expect(filename).toHaveClass("truncate");
    expect(filename.parentElement).toHaveClass("min-w-0", "max-w-full");
    expect(screen.getByText("1 KiB")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "提交 DOCX" })).toBeEnabled();
  });

  it("prevents a synchronous double click from uploading the same DOCX twice", async () => {
    const user = userEvent.setup();
    renderDetail(detail({ phase: "RUNNING", participant: true }, [problems[2]]));
    const file = new File(["docx"], "answer.docx", { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
    await user.upload(await screen.findByLabelText("DOCX 文件"), file);
    let resolveSubmit!: (value: Awaited<ReturnType<typeof api.submitContestOffice>>) => void;
    const submit = vi.spyOn(api, "submitContestOffice").mockReturnValue(new Promise((resolve) => { resolveSubmit = resolve; }));
    const button = screen.getByRole("button", { name: "提交 DOCX" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(submit).toHaveBeenCalledTimes(1);
    await act(async () => resolveSubmit({ submission: {
      id: 202, userId: 10, exerciseId: 6, studentDocName: "answer.docx", status: "COMPLETED", score: 100, teacherComment: null, judgeVersion: "office-docx-v1", errorCategory: null, judgedAt: "2026-09-01T01:31:00Z", createdAt: "2026-09-01T01:30:00Z", contestProblemId: 73,
      resultDetail: { judgeVersion: "office-docx-v1", totalScore: 100, earnedScore: 100, passed: true, totalErrorCount: 0, truncated: false, items: [] },
    } }));
    expect(await screen.findByText("判题完成")).toBeInTheDocument();
  });

  it("refreshes UPCOMING to RUNNING from the server without a browser reload", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-01T00:30:00Z"));
    vi.spyOn(api, "getContest")
      .mockResolvedValueOnce({ detail: detail({ phase: "UPCOMING", participant: true }, [problems[0]]) })
      .mockResolvedValue({ detail: detail({ phase: "RUNNING", participant: true }, [problems[0]]) });
    vi.spyOn(api, "getLanguages").mockResolvedValue({ languages: defaultLanguages });
    render(<MemoryRouter initialEntries={["/contests/7"]}><Routes><Route path="/contests/:id" element={<ContestDetail />} /></Routes></MemoryRouter>);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(screen.queryByRole("button", { name: "提交代码" })).not.toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(CONTEST_REFRESH_MS); });
    expect(screen.getByRole("button", { name: "提交代码" })).toBeInTheDocument();
  });

  it("refreshes RUNNING to ENDED and closes submission UI", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-01T01:30:00Z"));
    vi.spyOn(api, "getContest")
      .mockResolvedValueOnce({ detail: detail({ phase: "RUNNING", participant: true }, [problems[0]]) })
      .mockResolvedValue({ detail: detail({ phase: "ENDED", participant: true }, [problems[0]]) });
    vi.spyOn(api, "getLanguages").mockResolvedValue({ languages: defaultLanguages });
    render(<MemoryRouter initialEntries={["/contests/7"]}><Routes><Route path="/contests/:id" element={<ContestDetail />} /></Routes></MemoryRouter>);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(screen.getByRole("button", { name: "提交代码" })).toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(CONTEST_REFRESH_MS); });
    expect(screen.queryByRole("button", { name: "提交代码" })).not.toBeInTheDocument();
    expect(screen.getByText("比赛已结束；历史题目仍可查看。")).toBeInTheDocument();
  });

  it("paginates all 126 participants instead of treating the first page as complete", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, problems), 126);
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    const heading = await screen.findByText("参赛者（126）");
    const card = heading.closest('[data-slot="card"]') as HTMLElement;
    expect(within(card).getByText("1 / 7")).toBeInTheDocument();
    const participantNext = within(card).getAllByRole("button", { name: "下一页" }).find((button) => !button.hasAttribute("disabled"));
    expect(participantNext).toBeDefined();
    await user.click(participantNext!);
    await waitFor(() => expect(api.listContestParticipants).toHaveBeenCalledWith(7, { page: 2, pageSize: 20 }));
    expect(within(card).getByText("2 / 7")).toBeInTheDocument();
  });

  it("adds an algorithm problem from the catalog without a manual database ID", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, []));
    const add = vi.spyOn(api, "addContestProblem").mockResolvedValue({ contestProblem: problems[0] });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    expect(await screen.findByText("最大子数组")).toBeInTheDocument();
    expect(screen.queryByLabelText(/Problem ID/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "选择 最大子数组" }));
    await user.click(screen.getByRole("button", { name: "添加 1 道题" }));
    await waitFor(() => expect(add).toHaveBeenCalledWith(7, "ALGORITHM", 44));
  });

  it("shows DOCX completeness and disables exercises missing either document", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, []));
    vi.mocked(api.listManageDocExercises).mockResolvedValue({ total: 2, page: 1, pageSize: 20, exercises: [
      { id: 50, title: "Word 排版基础", difficulty: "MEDIUM", visible: true, contentVisibility: "CONTEST_ONLY", hasStarterDoc: true, starterDocName: "starter.docx", hasTeacherDoc: true, createdBy: 2, creatorUsername: "teacher", submissionCount: 0, createdAt: "2026-08-01T00:00:00Z" },
      { id: 51, title: "缺少起始文档", difficulty: "EASY", visible: true, contentVisibility: "PUBLIC", hasStarterDoc: false, starterDocName: null, hasTeacherDoc: true, createdBy: 2, creatorUsername: "teacher", submissionCount: 0, createdAt: "2026-08-01T00:00:00Z" },
    ] });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.selectOptions(await screen.findByLabelText("添加题型"), "OFFICE_DOCX");
    expect(await screen.findByText("Word 排版基础")).toBeInTheDocument();
    expect(screen.getByText(/Starter \+ Reference 已齐全/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "选择 缺少起始文档" })).toBeDisabled();
  });

  it("adds an Office choice from the safe management catalog", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, []));
    vi.mocked(api.listManageOfficeQuestions).mockResolvedValue({
      total: 1,
      page: 1,
      pageSize: 20,
      questions: [{
        id: 60,
        appType: "WORD",
        category: "基础",
        difficulty: "EASY",
        questionType: "SINGLE_CHOICE",
        content: "保存文档的快捷键是？",
        visible: true,
        contentVisibility: "CONTEST_ONLY",
        createdBy: 2,
        creatorUsername: "teacher",
        submissionCount: 0,
        createdAt: "2026-08-01T00:00:00Z",
      }],
    });
    const add = vi.spyOn(api, "addContestProblem").mockResolvedValue({ contestProblem: choiceProblem });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.selectOptions(await screen.findByLabelText("添加题型"), "OFFICE_CHOICE");
    await user.click(await screen.findByRole("checkbox", { name: "选择 保存文档的快捷键是？" }));
    await user.click(screen.getByRole("button", { name: "添加 1 道题" }));
    await waitFor(() => expect(add).toHaveBeenCalledWith(7, "OFFICE_CHOICE", 60));
  });

  it("searches and adds a participant by username instead of a user ID field", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, []));
    const add = vi.spyOn(api, "addContestParticipant").mockResolvedValue({ participant: { id: 1, userId: 10, username: "stage_student", addedBy: 2, joinedAt: "2026-08-01T00:00:00Z" } });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    expect(await screen.findByText("stage_student")).toBeInTheDocument();
    expect(screen.queryByLabelText(/学生用户 ID/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "添加" }));
    await waitFor(() => expect(add).toHaveBeenCalledWith(7, 10));
  });

  it("requires confirmation before cancelling and calls the API once", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "PUBLISHED", phase: "UPCOMING" }, problems));
    const cancel = vi.spyOn(api, "cancelContest").mockResolvedValue({ detail: detail({ status: "CANCELLED", phase: "CANCELLED" }, problems) });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.click(await screen.findByRole("button", { name: "取消比赛" }));
    expect(cancel).not.toHaveBeenCalled();
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "确认取消比赛" }));
    await waitFor(() => expect(cancel).toHaveBeenCalledTimes(1));
  });

  it("locks a publish confirmation against duplicate click requests", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, problems));
    let resolvePublish!: () => void;
    const publish = vi.spyOn(api, "publishContest").mockReturnValue(new Promise((resolve) => { resolvePublish = () => resolve({ detail: detail({ status: "PUBLISHED", phase: "UPCOMING" }, problems) }); }));
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.click(await screen.findByRole("button", { name: "发布比赛" }));
    const confirm = screen.getByRole("button", { name: "确认发布比赛" });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(publish).toHaveBeenCalledTimes(1);
    await act(async () => resolvePublish());
  });

  it("requires confirmation before deleting a draft", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, problems));
    const remove = vi.spyOn(api, "deleteContest").mockResolvedValue({ deleted: true });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /><Route path="/contests" element={<div>比赛列表</div>} /></Routes></MemoryRouter>);
    await user.click(await screen.findByRole("button", { name: "删除草稿" }));
    expect(remove).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "确认删除草稿" }));
    await waitFor(() => expect(remove).toHaveBeenCalledTimes(1));
  });

  it("requires confirmation before removing a contest problem", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, problems));
    const remove = vi.spyOn(api, "removeContestProblem").mockResolvedValue({ removed: true });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.click(await screen.findByRole("button", { name: "移除 A + B" }));
    expect(remove).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "确认移除" }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(7, 71));
  });

  it("requires confirmation before removing a participant", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ status: "DRAFT", phase: "DRAFT" }, problems), 1);
    const remove = vi.spyOn(api, "removeContestParticipant").mockResolvedValue({ removed: true });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.click(await screen.findByRole("button", { name: "移除" }));
    expect(remove).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "确认移除" }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(7, 101));
  });

  it("validates a new contest form before sending it to the backend", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    const create = vi.spyOn(api, "createContest");
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/new"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.click(screen.getByRole("button", { name: "创建比赛" }));
    expect(screen.getByText("标题不能为空。")).toBeInTheDocument();
    expect(screen.getByText("请选择开始时间。")).toBeInTheDocument();
    expect(screen.getByText("请选择结束时间。")).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });

  it("rejects an end time that is not later than the start time", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    const create = vi.spyOn(api, "createContest");
    render(<MemoryRouter initialEntries={["/admin/contests/new"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    fireEvent.change(screen.getByLabelText("标题"), { target: { value: "时间边界测试" } });
    fireEvent.change(screen.getByLabelText("开始时间"), { target: { value: "2030-08-15T10:00" } });
    fireEvent.change(screen.getByLabelText("结束时间"), { target: { value: "2030-08-15T09:00" } });
    fireEvent.click(screen.getByRole("button", { name: "创建比赛" }));
    expect(screen.getByText("结束时间必须晚于开始时间。")).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });

  it("does not load mutable selectors or expose controls for a RUNNING contest", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    vi.spyOn(api, "getContest").mockResolvedValue({ detail: detail({ phase: "RUNNING", participant: false }, problems) });
    vi.spyOn(api, "listContestParticipants").mockResolvedValue({ total: 0, page: 1, pageSize: 20, participants: [] });
    const catalog = vi.spyOn(api, "listManageProblems");
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    expect(await screen.findByLabelText("标题")).toBeDisabled();
    expect(screen.getByRole("button", { name: "保存设置" })).toBeDisabled();
    expect(screen.queryByLabelText("添加题型")).not.toBeInTheDocument();
    expect(catalog).not.toHaveBeenCalled();
  });

  it("renders frozen SCORE standings without any post-freeze live values", async () => {
    const frozen: ContestStanding = {
      contestId: 7, scoringMode: "SCORE", phase: "RUNNING", frozen: true, managerView: false,
      freezeAt: "2026-09-01T02:00:00Z", generatedAt: "2026-09-01T02:01:00Z",
      entries: [{ rank: 1, userId: 10, username: "student", totalScore: 100, solved: 0, penaltyMinutes: 0,
        problems: [{ contestProblemId: 71, label: "A", score: 100, solved: true, attempts: 1, penaltyMinutes: null }] }],
    };
    vi.spyOn(api, "getContestStandings").mockResolvedValue({ standings: frozen });
    render(<MemoryRouter initialEntries={["/contests/7/standings"]}><Routes><Route path="/contests/:id/standings" element={<ContestStandings />} /></Routes></MemoryRouter>);
    expect(await screen.findByText("比赛已封榜；当前排名仅展示封榜前提交。")).toBeInTheDocument();
    expect(screen.getAllByText("100")).toHaveLength(2);
    expect(screen.queryByText("管理员实时榜单")).not.toBeInTheDocument();
  });

  it("renders ICPC standings and the manager live-view banner", async () => {
    const live: ContestStanding = {
      contestId: 7, scoringMode: "ICPC", phase: "RUNNING", frozen: false, managerView: true,
      freezeAt: "2026-09-01T02:00:00Z", generatedAt: "2026-09-01T02:01:00Z",
      entries: [{ rank: 1, userId: 2, username: "teacher", totalScore: 1, solved: 1, penaltyMinutes: 42,
        problems: [{ contestProblemId: 71, label: "A", score: 100, solved: true, attempts: 2, penaltyMinutes: 42 }] }],
    };
    vi.spyOn(api, "getContestStandings").mockResolvedValue({ standings: live });
    render(<MemoryRouter initialEntries={["/contests/7/standings"]}><Routes><Route path="/contests/:id/standings" element={<ContestStandings />} /></Routes></MemoryRouter>);
    expect(await screen.findByText("ICPC：解题数优先，罚时次之")).toBeInTheDocument();
    expect(screen.getByText("管理员实时榜单：学生视图会在封榜后隐藏封榜后的提交。")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("AC · 42分")).toBeInTheDocument();
  });

  it("limits an ICPC draft catalog to algorithms and validates the freeze window", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    render(<MemoryRouter initialEntries={["/admin/contests/new"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("计分模式"), "ICPC");
    expect(screen.getByText("ICPC 模式仅允许算法题。")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("标题"), { target: { value: "ICPC" } });
    fireEvent.change(screen.getByLabelText("开始时间"), { target: { value: "2030-09-01T10:00" } });
    fireEvent.change(screen.getByLabelText("结束时间"), { target: { value: "2030-09-01T12:00" } });
    fireEvent.change(screen.getByLabelText("封榜时间（可选）"), { target: { value: "2030-09-01T12:00" } });
    fireEvent.click(screen.getByRole("button", { name: "创建比赛" }));
    expect(screen.getByText("封榜时间必须位于开始和结束时间之间。")).toBeInTheDocument();
  });

  it("requires rejudge confirmation, sends one request, and reports progress", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    mockManage(detail({ phase: "RUNNING", participant: false }, problems));
    const request = vi.spyOn(api, "rejudgeContest").mockResolvedValue({ batch: {
      batch: { id: 91, contestId: 7, contestProblemId: null, requestedSubmissionId: null, requestedBy: 2,
        status: "RUNNING", totalCount: 3, queuedCount: 2, completedCount: 1, failedCount: 0,
        createdAt: "2026-09-01T01:00:00Z", completedAt: null }, items: [],
    } });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/contests/7"]}><Routes><Route path="/admin/contests/:id" element={<ContestManage />} /></Routes></MemoryRouter>);
    await user.click(await screen.findByRole("button", { name: "重判全部算法提交" }));
    expect(request).not.toHaveBeenCalled();
    const confirm = screen.getByRole("button", { name: "确认创建重判" });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    await waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("status")).toHaveTextContent("批次 #91 · RUNNING · 已完成 1 / 3");
    expect(screen.getByText(/Office 题暂不支持重判/)).toBeInTheDocument();
  });
});
