import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Submission, Verdict } from "@/lib/api";
import { SubmissionResultCard } from "./SubmissionResultCard";

function submission(verdict: Verdict, overrides: Partial<Submission> = {}): Submission {
  return {
    id: 24,
    verdict,
    timeMs: 31,
    memoryKb: 18 * 1024,
    passed: verdict === "AC" ? 10 : 7,
    total: 10,
    language: "python",
    code: "print(1)",
    createdAt: "2026-08-14T00:00:00Z",
    ...overrides,
  };
}

describe("SubmissionResultCard", () => {
  it.each([
    ["PENDING" as const, "正在排队..."],
    ["JUDGING" as const, "正在判题..."],
  ])("renders %s as an in-progress state", (verdict, message) => {
    render(<SubmissionResultCard submission={submission(verdict)} />);
    expect(screen.getByText(message)).toBeInTheDocument();
    expect(screen.queryByText(/测试点：/)).not.toBeInTheDocument();
  });

  it.each([
    ["AC" as const, "通过"],
    ["WA" as const, "答案错误"],
    ["CE" as const, "编译错误"],
  ])("renders structured resource details for %s", (verdict, label) => {
    render(<SubmissionResultCard submission={submission(verdict, verdict === "CE" ? { message: "line 1: syntax error" } : {})} />);
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.getByText("Submission #24")).toBeInTheDocument();
    expect(screen.getByText("31 ms")).toBeInTheDocument();
    expect(screen.getByText("18.0 MB")).toBeInTheDocument();
    expect(screen.getByText(`${verdict === "AC" ? 10 : 7} / 10`)).toBeInTheDocument();
    if (verdict === "CE") expect(screen.getByText("line 1: syntax error")).toBeInTheDocument();
  });
});
