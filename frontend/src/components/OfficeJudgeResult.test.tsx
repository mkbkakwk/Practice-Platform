import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { StudentDocSubmission } from "@/lib/api";
import { OfficeJudgeResult } from "./OfficeJudgeResult";

function studentSubmission(overrides: Partial<StudentDocSubmission> = {}): StudentDocSubmission {
  return {
    id: 201,
    userId: 10,
    exerciseId: 6,
    studentDocName: "answer.docx",
    status: "COMPLETED",
    score: 100,
    teacherComment: null,
    judgeVersion: "office-docx-v1",
    resultDetail: {
      judgeVersion: "office-docx-v1",
      totalScore: 100,
      earnedScore: 100,
      passed: true,
      totalErrorCount: 0,
      truncated: false,
      items: [],
    },
    errorCategory: null,
    judgedAt: "2026-08-14T00:00:01Z",
    createdAt: "2026-08-14T00:00:00Z",
    contestProblemId: 73,
    ...overrides,
  };
}

describe("OfficeJudgeResult", () => {
  it("renders a completed full-score student result", () => {
    render(<OfficeJudgeResult submission={studentSubmission()} />);
    expect(screen.getByText("判题完成")).toBeInTheDocument();
    expect(screen.getByText("100")).toBeInTheDocument();
    expect(screen.getByText("/ 100")).toBeInTheDocument();
  });

  it("shows sanitized structured differences with expansion and truncation", async () => {
    const user = userEvent.setup();
    const items = Array.from({ length: 6 }, (_, index) => ({
      ruleId: `font-${index}`,
      target: `第 ${index + 1} 段字体`,
      expected: "宋体",
      actual: "微软雅黑",
      score: 10,
      earned: 0,
      passed: false,
      message: "字体不符合要求",
    }));
    render(<OfficeJudgeResult submission={studentSubmission({
      status: "NEEDS_REVIEW",
      score: 56,
      resultDetail: {
        judgeVersion: "office-docx-v1",
        totalScore: 100,
        earnedScore: 56,
        passed: false,
        totalErrorCount: 14,
        truncated: true,
        items,
      },
    })} />);
    expect(screen.getByText("待老师复核")).toBeInTheDocument();
    expect(screen.getByText(/仅显示服务端返回的部分结果/)).toBeInTheDocument();
    expect(screen.queryByText("第 6 段字体 · 字体不符合要求")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "展开全部（6）" }));
    expect(screen.getByText("第 6 段字体 · 字体不符合要求")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("SECRET_TEACHER_REFERENCE_9f82c7");
  });

  it("renders a sanitized failure without internal comparison details", () => {
    render(<OfficeJudgeResult submission={studentSubmission({
      status: "FAILED",
      score: null,
      resultDetail: {
        judgeVersion: "office-docx-v1",
        totalScore: 100,
        earnedScore: 0,
        passed: false,
        totalErrorCount: 0,
        truncated: false,
        items: [],
      },
      errorCategory: "INVALID_DOCUMENT",
    })} />);
    expect(screen.getByRole("alert")).toHaveTextContent("文档未能完成判题");
    expect(screen.queryByText(/老师文档/)).not.toBeInTheDocument();
  });
});
