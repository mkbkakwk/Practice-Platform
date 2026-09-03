import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, type ContestAnalytics } from "@/lib/api";
import ContestAnalyticsPage from "./ContestAnalytics";

const analytics: ContestAnalytics = {
  contestId: 9, title: "数据分析赛", scoringMode: "SCORE", phase: "ENDED", generatedAt: "2026-11-01T12:00:00Z",
  overview: { participantCount: 2, activeParticipantCount: 1, inactiveParticipantCount: 1, totalSubmissionCount: 3, algorithmSubmissionCount: 1, choiceSubmissionCount: 1, docxSubmissionCount: 1, firstSubmissionAt: null, lastSubmissionAt: null, averageTotalScore: 78, maxTotalScore: 156, minTotalScore: 0, fullScoreParticipantCount: 0, averageSolved: null, maxSolved: null, averagePenaltyAmongSolvedParticipants: null },
  problems: [{ contestProblemId: 90, label: "A", displayOrder: 1, title: "长标题算法题", problemType: "ALGORITHM", submissionCount: 1, uniqueSubmitterCount: 1, participationRate: 0.5, successParticipantCount: 1, successRate: 0.5, infrastructureFailureCount: 0, validJudgedSubmissionCount: 1, acceptedSubmissionCount: 1, submissionAcceptanceRate: 1, correctSubmissionCount: null, validSubmissionCount: null, correctSubmissionRate: null, scoredParticipantCount: null, averageBestScore: null, medianBestScore: null, perfectScoreParticipantCount: null, perfectScoreRate: null, needsReviewSubmissionCount: null }],
  timeline: [{ startAt: "2026-11-01T10:00:00Z", endAt: "2026-11-01T10:10:00Z", submissionCount: 3, algorithmCount: 1, choiceCount: 1, docxCount: 1, successCount: 2 }],
  distribution: [{ label: "0%", participantCount: 1 }, { label: "100%", participantCount: 1 }],
};

function response(query = "") {
  return { participants: { page: 1, pageSize: 20, total: query ? 1 : 40, participants: [{ userId: 1, username: "student-alpha", rank: 1, totalSubmissionCount: 3, submittedProblemCount: 3, successfulProblemCount: 2, lastSubmissionAt: "2026-11-01T10:10:00Z", totalScore: 156, solved: null, penaltyMinutes: null }] } };
}

describe("ContestAnalytics", () => {
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); });

  it("renders derived metrics and debounces participant search", async () => {
    const overview = vi.spyOn(api, "getContestAnalytics").mockResolvedValue({ analytics });
    const participants = vi.spyOn(api, "getContestAnalyticsParticipants").mockImplementation(async (_id, params) => response(params?.query ?? ""));
    render(<MemoryRouter initialEntries={["/admin/contests/9/analytics"]}><Routes><Route path="/admin/contests/:id/analytics" element={<ContestAnalyticsPage />} /></Routes></MemoryRouter>);
    expect(await screen.findByText("比赛数据分析")).toBeInTheDocument();
    expect(screen.getByTitle("长标题算法题")).toBeInTheDocument();
    expect(screen.getByText("总提交")).toBeInTheDocument();
    expect(overview).toHaveBeenCalledWith(9, expect.anything());
    expect(participants).toHaveBeenCalledWith(9, { page: 1, pageSize: 20, query: "" }, expect.anything());
    fireEvent.change(screen.getByLabelText("搜索用户名"), { target: { value: "alpha" } });
    expect(participants).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(participants).toHaveBeenLastCalledWith(
      9, { page: 1, pageSize: 20, query: "alpha" }, expect.anything(),
    ));
  });

  it("keeps an error visible while retaining the previous analytics result", async () => {
    vi.spyOn(api, "getContestAnalytics").mockResolvedValue({ analytics });
    vi.spyOn(api, "getContestAnalyticsParticipants").mockResolvedValue(response());
    render(<MemoryRouter initialEntries={["/admin/contests/9/analytics"]}><Routes><Route path="/admin/contests/:id/analytics" element={<ContestAnalyticsPage />} /></Routes></MemoryRouter>);
    expect(await screen.findByText("题目分析")).toBeInTheDocument();
    vi.spyOn(api, "getContestAnalytics").mockRejectedValueOnce(new Error("offline"));
    // A new page request makes the failed request observable without replacing the old page content.
    screen.getByText("下一页").click();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("数据分析加载失败"));
    expect(screen.getByText("题目分析")).toBeInTheDocument();
  });
});
