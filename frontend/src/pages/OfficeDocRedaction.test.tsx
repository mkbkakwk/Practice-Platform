import { render, screen } from "@testing-library/react";
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
});
