package com.oj.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class ContestDtos {
    private ContestDtos() {}

    public record Summary(
            Integer id, String title, String description, String status, String phase,
            String accessType, Integer ownerId, String ownerUsername,
            String scoringMode, Instant startAt, Instant endAt, Instant freezeAt, boolean participant,
            Instant createdAt, Instant updatedAt) {}

    public record Detail(Summary contest, List<ProblemItem> problems) {}

    public record ProblemItem(
            Long contestProblemId, String problemType, Integer problemId,
            Integer displayOrder, String label, String title, String difficulty,
            String slug, Object content) {}

    public record Participant(
            Long id, Integer userId, String username, Integer addedBy, Instant joinedAt) {}

    public record StudentOption(Integer id, String username, String role) {}

    public record ChoiceSubmission(
            Integer recordId, Long contestProblemId, List<String> selected,
            boolean correct, LocalDateTime createdAt) {
        public ChoiceSubmission {
            selected = List.copyOf(selected);
        }
    }

    public record Standing(
            Integer contestId, String scoringMode, String phase, boolean frozen, boolean managerView, Instant freezeAt,
            Instant generatedAt, List<StandingEntry> entries) {}

    public record StandingEntry(
            int rank, Integer userId, String username, int totalScore, int solved,
            int penaltyMinutes, List<StandingProblem> problems) {}

    public record StandingProblem(
            Long contestProblemId, String label, Integer score, boolean solved,
            int attempts, Integer penaltyMinutes) {}

    public record RejudgeBatch(
            Long id, Integer contestId, Long contestProblemId, Integer requestedSubmissionId,
            Integer requestedBy, String status, int totalCount, int queuedCount,
            int completedCount, int failedCount, Instant createdAt, Instant completedAt) {}

    public record RejudgeBatchItem(
            Long id, Integer submissionId, int judgeGeneration, String status,
            Instant createdAt, Instant completedAt) {}

    public record RejudgeBatchDetail(RejudgeBatch batch, List<RejudgeBatchItem> items) {}

    /** Manager-only, intentionally minimal algorithm submission view for targeted rejudge. */
    public record RejudgeableSubmission(
            Integer id, Long contestProblemId, String problemLabel, Integer userId,
            String username, String verdict, int judgeGeneration, Instant createdAt) {}

    /** Manager-only, derived contest teaching analytics.  It intentionally contains no submissions or source data. */
    public record Analytics(
            Integer contestId, String title, String scoringMode, String phase, Instant generatedAt,
            Overview overview, List<ProblemAnalytics> problems, List<TimelineBucket> timeline,
            List<DistributionBucket> distribution) {}

    public record Overview(int participantCount, int activeParticipantCount, int inactiveParticipantCount,
            int totalSubmissionCount, int algorithmSubmissionCount, int choiceSubmissionCount, int docxSubmissionCount,
            Instant firstSubmissionAt, Instant lastSubmissionAt, Double averageTotalScore, Integer maxTotalScore,
            Integer minTotalScore, Integer fullScoreParticipantCount, Double averageSolved, Integer maxSolved,
            Double averagePenaltyAmongSolvedParticipants) {}

    public record ProblemAnalytics(Long contestProblemId, String label, int displayOrder, String title,
            String problemType, int submissionCount, int uniqueSubmitterCount, double participationRate,
            int successParticipantCount, double successRate, int infrastructureFailureCount,
            Integer validJudgedSubmissionCount, Integer acceptedSubmissionCount, Double submissionAcceptanceRate,
            Integer correctSubmissionCount, Integer validSubmissionCount, Double correctSubmissionRate,
            Integer scoredParticipantCount, Double averageBestScore, Double medianBestScore,
            Integer perfectScoreParticipantCount, Double perfectScoreRate, Integer needsReviewSubmissionCount) {}

    public record TimelineBucket(Instant startAt, Instant endAt, int submissionCount, int algorithmCount,
            int choiceCount, int docxCount, int successCount) {}

    public record DistributionBucket(String label, int participantCount) {}

    public record AnalyticsParticipant(Integer userId, String username, Integer rank, int totalSubmissionCount,
            int submittedProblemCount, int successfulProblemCount, Instant lastSubmissionAt, Integer totalScore,
            Integer solved, Integer penaltyMinutes) {}

    public record AnalyticsParticipants(int page, int pageSize, long total, List<AnalyticsParticipant> participants) {}
}
