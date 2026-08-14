package com.oj.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class OfficeSubmissionDtos {
    private OfficeSubmissionDtos() {}

    public record StudentResultItem(
            String ruleId,
            String target,
            Object expected,
            Object actual,
            int score,
            int earned,
            boolean passed,
            String message) {}

    public record StudentResultDetail(
            String judgeVersion,
            int totalScore,
            int earnedScore,
            boolean passed,
            List<StudentResultItem> items,
            int totalErrorCount,
            boolean truncated) {
        public StudentResultDetail {
            items = List.copyOf(items);
        }
    }

    public record StudentSubmission(
            Integer id,
            Integer userId,
            Integer exerciseId,
            Long contestProblemId,
            String studentDocName,
            String status,
            Integer score,
            String teacherComment,
            String judgeVersion,
            StudentResultDetail resultDetail,
            String errorCategory,
            LocalDateTime judgedAt,
            LocalDateTime createdAt) {}

    public record ReviewerSubmission(
            Integer id,
            Integer userId,
            Integer exerciseId,
            Long contestProblemId,
            String studentDocName,
            String autoResult,
            String compareResult,
            String status,
            Integer score,
            String teacherComment,
            String judgeVersion,
            Map<String, Object> resultDetail,
            String errorCategory,
            LocalDateTime judgedAt,
            LocalDateTime createdAt) {}

    public record SubmissionSummary(
            Integer id,
            Integer exerciseId,
            Long contestProblemId,
            Integer userId,
            String studentDocName,
            String status,
            Integer score,
            LocalDateTime createdAt) {}

    public record StudentSubmissionResponse(StudentSubmission submission) {}

    public record ReviewerSubmissionResponse(ReviewerSubmission submission) {}

    public record SubmissionListResponse(
            long total,
            int page,
            int pageSize,
            List<SubmissionSummary> submissions) {
        public SubmissionListResponse {
            submissions = List.copyOf(submissions);
        }
    }
}
