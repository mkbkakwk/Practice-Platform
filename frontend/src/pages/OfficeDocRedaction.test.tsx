import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  api,
  type DocExerciseDetail,
  type ReviewerDocSubmission,
  type StudentDocSubmission,
} from "@/lib/api";
import OfficeDocExerciseDetail from "./OfficeDocExerciseDetail";
import OfficeDocReview from "./OfficeDocReview";
import OfficeDocForm from "./OfficeDocForm";

const auth = vi.hoisted(() => ({
  user: { id: 10, username: "student", role: "USER" as const } as
    | { id: number; username: string; role: "USER" | "TEACHER" | "ADMIN" }
    | null,
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: auth.user, loading: false, logout: vi.fn() }),
}));

const exercise: DocExerciseDetail = {
  id: 7,
  title: "DOCX 排版",
  difficulty: "EASY",
  description: "按要求设置格式",
  starterDocName: "starter.docx",
  teacherDocName: "reference.docx",
  visible: true,
  contentVisibility: "PUBLIC",
  createdBy: 2,
  creatorUsername: "teacher",
  createdAt: "2026-08-01T00:00:00Z",
};

const safeStudentSubmission: StudentDocSubmission = {
  id: 31,
  userId: 10,
  exerciseId: 7,
  contestProblemId: null,
  studentDocName: "student.docx",
  status: "NEEDS_REVIEW",
  score: 56,
  teacherComment: null,
  judgeVersion: "office-docx-v1",
  resultDetail: {
    judgeVersion: "office-docx-v1",
    totalScore: 100,
    earnedScore: 56,
    passed: false,
    totalErrorCount: 1,
    truncated: false,
    items: [{
      ruleId: "paragraph-1-run-0-font",
      target: "第 2 段第 1 文本片段",
      expected: "宋体",
      actual: "微软雅黑",
      score: 1,
      earned: 0,
      passed: false,
      message: "字体不符合要求",
    }],
  },
  errorCategory: null,
  judgedAt: "2026-08-01T00:00:02Z",
  createdAt: "2026-08-01T00:00:00Z",
};

describe("Office DOCX response redaction", () => {
  beforeEach(() => {
    auth.user = { id: 10, username: "student", role: "USER" };
    vi.spyOn(api, "getDocExercise").mockResolvedValue({ exercise });
  });

  it("renders only sanitized student result details without a teacher document comparison", async () => {
    vi.spyOn(api, "listDocSubmissions").mockResolvedValue({
      total: 1,
      page: 1,
      pageSize: 1,
      submissions: [{
        id: 31,
        exerciseId: 7,
        userId: 10,
        studentDocName: "student.docx",
        status: "NEEDS_REVIEW",
        score: 56,
        createdAt: "2026-08-01T00:00:00Z",
      }],
    });
    vi.spyOn(api, "getDocSubmission").mockResolvedValue({ submission: safeStudentSubmission });

    render(
      <MemoryRouter initialEntries={["/office/docs/7"]}>
        <Routes><Route path="/office/docs/:id" element={<OfficeDocExerciseDetail />} /></Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("安全判题反馈")).toBeInTheDocument();
    expect(screen.getByText(/你的结果：微软雅黑/)).toBeInTheDocument();
    expect(screen.getByText(/要求：宋体/)).toBeInTheDocument();
    expect(screen.queryByText(/老师文档/)).not.toBeInTheDocument();
    expect(screen.queryByText(/SECRET_TEACHER_REFERENCE_9f82c7/)).not.toBeInTheDocument();
  });

  it("shows the three-step practice flow and prevents duplicate starter downloads", async () => {
    vi.spyOn(api, "listDocSubmissions").mockResolvedValue({ total: 0, page: 1, pageSize: 1, submissions: [] });
    let resolveDownload!: () => void;
    const download = vi.spyOn(api, "downloadStarterDoc")
      .mockReturnValue(new Promise<void>((resolve) => { resolveDownload = resolve; }));
    render(
      <MemoryRouter initialEntries={["/office/docs/7"]}>
        <Routes><Route path="/office/docs/:id" element={<OfficeDocExerciseDetail />} /></Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("① 下载待修改文件")).toBeInTheDocument();
    expect(screen.getByText(/② 在本地 Word \/ WPS/)).toBeInTheDocument();
    expect(screen.getByText("③ 上传修改后的 DOCX")).toBeInTheDocument();
    const button = screen.getByRole("button", { name: "下载 starter.docx" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(download).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "下载中..." })).toBeDisabled();
    await act(async () => resolveDownload());
    expect(await screen.findByRole("button", { name: "下载 starter.docx" })).toBeEnabled();
  });

  it("keeps full comparison rows on the separate reviewer-only client contract", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    const reviewer: ReviewerDocSubmission = {
      ...safeStudentSubmission,
      autoResult: "{}",
      compareResult: JSON.stringify([{
        index: 0,
        studentText: "student answer",
        teacherText: "SECRET_TEACHER_REFERENCE_9f82c7",
        diffs: [],
        match: false,
      }]),
    };
    vi.spyOn(api, "getDocSubmissionForReview").mockResolvedValue({ submission: reviewer });

    render(
      <MemoryRouter initialEntries={["/admin/office-doc/review/31"]}>
        <Routes><Route path="/admin/office-doc/review/:id" element={<OfficeDocReview />} /></Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText(/老师：SECRET_TEACHER_REFERENCE_9f82c7/)).toBeInTheDocument();
    expect(api.getDocSubmissionForReview).toHaveBeenCalledWith(31);
  });

  it("manages independent Starter and Reference documents before marking an exercise complete", async () => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    vi.spyOn(api, "getDocExercise").mockResolvedValue({
      exercise: { ...exercise, starterDocName: null, teacherDocName: null },
    });
    const uploadStarter = vi.spyOn(api, "uploadStarterDoc").mockResolvedValue({ starterDocName: "starter.docx" });
    const uploadReference = vi.spyOn(api, "uploadTeacherDoc").mockResolvedValue({ teacherDocName: "reference.docx" });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/admin/office-doc/7/edit"]}><Routes><Route path="/admin/office-doc/:id/edit" element={<OfficeDocForm mode="edit" />} /></Routes></MemoryRouter>);

    await screen.findByText(/尚未完成/);
    const starter = new File(["starter"], "starter.docx", { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
    await user.upload(screen.getByLabelText("学生待修改文件"), starter);
    await user.click(screen.getByRole("button", { name: "上传 Starter" }));
    await waitFor(() => expect(uploadStarter).toHaveBeenCalledWith(7, starter));

    const reference = new File(["reference"], "reference.docx", { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
    await user.upload(screen.getByLabelText("教师参考文档"), reference);
    await user.click(screen.getByRole("button", { name: "上传 Reference" }));
    await waitFor(() => expect(uploadReference).toHaveBeenCalledWith(7, reference));
    expect(screen.getByText(/Starter 与 Reference 已齐全/)).toBeInTheDocument();
  });
});
