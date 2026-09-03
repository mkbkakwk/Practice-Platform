package com.oj.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.ContestException;
import com.oj.contest.ContestLifecycle;
import com.oj.contest.ContestPhase;
import com.oj.contest.ContestProblemType;
import com.oj.dto.ContestDtos;
import com.oj.entity.ContestEntity;
import com.oj.entity.ContestProblemEntity;
import com.oj.mapper.ContestMapper;
import com.oj.mapper.ContestProblemMapper;
import com.oj.reliability.JudgeMessage;
import com.oj.reliability.JudgeOutboxRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Queues algorithm rejudges without creating a second judge delivery path.  Each
 * generation is an independent outbox event; Worker CAS checks make old deliveries
 * harmless once a newer generation has been queued.
 */
@Service
public class ContestRejudgeService {
    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final JudgeOutboxRepository outbox;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ContestRejudgeService(ContestMapper contestMapper, ContestProblemMapper contestProblemMapper,
                                 JudgeOutboxRepository outbox, JdbcTemplate jdbc,
                                 ObjectMapper objectMapper, Clock clock) {
        this.contestMapper = contestMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ContestDtos.RejudgeBatchDetail rejudgeSubmission(int contestId, int submissionId) {
        ContestEntity contest = lockedManageableContest(contestId);
        SubmissionScope scope = jdbc.query("""
                SELECT submission.id, submission.contest_problem_id, problem.problem_type
                FROM "Submission" submission
                JOIN "ContestProblem" problem ON problem.id = submission.contest_problem_id
                WHERE submission.id = ? AND problem.contest_id = ?
                """, rs -> rs.next() ? new SubmissionScope(rs.getInt(1), rs.getLong(2), rs.getString(3)) : null,
                submissionId, contestId);
        if (scope == null) {
            throw ContestException.conflict("REJUDGE_SUBMISSION_NOT_IN_CONTEST", "提交不属于该比赛");
        }
        requireAlgorithm(scope.problemType());
        return queue(contest, scope.contestProblemId(), submissionId, List.of(scope.submissionId()));
    }

    @Transactional
    public ContestDtos.RejudgeBatchDetail rejudgeProblem(int contestId, long contestProblemId) {
        ContestEntity contest = lockedManageableContest(contestId);
        ContestProblemEntity problem = requireContestProblem(contestId, contestProblemId);
        requireAlgorithm(problem.getProblemType());
        List<Integer> submissionIds = jdbc.queryForList("""
                SELECT id FROM "Submission"
                WHERE contest_problem_id = ?
                ORDER BY id
                """, Integer.class, contestProblemId);
        return queue(contest, contestProblemId, null, submissionIds);
    }

    @Transactional
    public ContestDtos.RejudgeBatchDetail rejudgeContest(int contestId) {
        ContestEntity contest = lockedManageableContest(contestId);
        List<Integer> submissionIds = jdbc.queryForList("""
                SELECT submission.id
                FROM "Submission" submission
                JOIN "ContestProblem" problem ON problem.id = submission.contest_problem_id
                WHERE problem.contest_id = ? AND problem.problem_type = 'ALGORITHM'
                ORDER BY submission.id
                """, Integer.class, contestId);
        return queue(contest, null, null, submissionIds);
    }

    public ContestDtos.RejudgeBatchDetail batch(int contestId, long batchId) {
        ContestEntity contest = requireManageableContest(contestId);
        refreshBatch(batchId);
        ContestDtos.RejudgeBatch batch = jdbc.query("""
                SELECT id, contest_id, contest_problem_id, requested_submission_id, requested_by,
                       status, total_count, queued_count, completed_count, failed_count, created_at, completed_at
                FROM rejudge_batch WHERE id = ? AND contest_id = ?
                """, rs -> rs.next() ? batch(rs) : null, batchId, contest.getId());
        if (batch == null) throw ContestException.notFound();
        List<ContestDtos.RejudgeBatchItem> items = jdbc.query("""
                SELECT id, submission_id, judge_generation, status, created_at, completed_at
                FROM rejudge_batch_item WHERE batch_id = ? ORDER BY id
                """, (rs, row) -> new ContestDtos.RejudgeBatchItem(
                rs.getLong("id"), rs.getInt("submission_id"), rs.getInt("judge_generation"),
                rs.getString("status"), instant(rs, "created_at"), instant(rs, "completed_at")), batchId);
        return new ContestDtos.RejudgeBatchDetail(batch, items);
    }

    /**
     * A bounded, manager-only read model lets the UI request a targeted
     * submission rejudge without exposing another student's source code or a
     * general cross-user submission feed.
     */
    public Map<String, Object> rejudgeableSubmissions(int contestId, int page, int pageSize) {
        requireManageableContest(contestId);
        int offset = (page - 1) * pageSize;
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM "Submission" submission
                JOIN "ContestProblem" problem ON problem.id = submission.contest_problem_id
                WHERE problem.contest_id = ? AND problem.problem_type = 'ALGORITHM'
                """, Long.class, contestId);
        List<ContestDtos.RejudgeableSubmission> submissions = jdbc.query("""
                SELECT submission.id, submission.contest_problem_id, problem.label,
                       submission.user_id, user_row.username, submission.verdict,
                       submission.judge_generation, submission.created_at
                FROM "Submission" submission
                JOIN "ContestProblem" problem ON problem.id = submission.contest_problem_id
                JOIN "User" user_row ON user_row.id = submission.user_id
                WHERE problem.contest_id = ? AND problem.problem_type = 'ALGORITHM'
                ORDER BY submission.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new ContestDtos.RejudgeableSubmission(
                rs.getInt("id"), rs.getLong("contest_problem_id"), rs.getString("label"),
                rs.getInt("user_id"), rs.getString("username"), rs.getString("verdict"),
                rs.getInt("judge_generation"), instant(rs, "created_at")), contestId, pageSize, offset);
        return Map.of("total", total == null ? 0 : total, "page", page, "pageSize", pageSize,
                "submissions", submissions);
    }

    private ContestDtos.RejudgeBatchDetail queue(ContestEntity contest, Long contestProblemId,
                                                  Integer requestedSubmissionId, List<Integer> submissionIds) {
        if (submissionIds.isEmpty()) {
            throw ContestException.conflict("NO_REJUDGEABLE_SUBMISSIONS", "没有可重判的算法提交");
        }
        long batchId = jdbc.queryForObject("""
                INSERT INTO rejudge_batch
                    (contest_id, contest_problem_id, requested_submission_id, requested_by, status, total_count, queued_count)
                VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
                RETURNING id
                """, Long.class, contest.getId(), contestProblemId, requestedSubmissionId,
                CurrentUser.getId(), submissionIds.size(), submissionIds.size());
        for (int submissionId : submissionIds) {
            Integer generation = jdbc.query("""
                    UPDATE "Submission"
                    SET judge_generation = judge_generation + 1,
                        verdict = 'PENDING', judge_token = NULL, judge_lease_until = NULL,
                        judge_failure_category = NULL, message = '等待重判'
                    WHERE id = ?
                    RETURNING judge_generation
                    """, rs -> rs.next() ? rs.getInt(1) : null, submissionId);
            if (generation == null) continue; // concurrent deletion is harmless and cannot leak scope.
            jdbc.update("""
                    UPDATE rejudge_batch_item SET status = 'STALE', completed_at = NOW()
                    WHERE submission_id = ? AND judge_generation < ? AND status = 'QUEUED'
                    """, submissionId, generation);
            JudgeMessage message = JudgeMessage.initial(submissionId, generation);
            try {
                outbox.insert(message.eventId(), submissionId, generation,
                        objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to persist rejudge event", exception);
            }
            jdbc.update("""
                    INSERT INTO rejudge_batch_item (batch_id, submission_id, judge_generation)
                    VALUES (?, ?, ?)
                    """, batchId, submissionId, generation);
            refreshBatchesForSubmission(submissionId);
        }
        refreshBatch(batchId);
        return batch(contest.getId(), batchId);
    }

    private ContestEntity lockedManageableContest(int contestId) {
        ContestEntity contest = contestMapper.selectByIdForUpdate(contestId);
        if (contest == null) throw ContestException.notFound();
        requireRejudgePhase(contest);
        requireManage(contest);
        return contest;
    }

    private ContestEntity requireManageableContest(int contestId) {
        ContestEntity contest = contestMapper.selectById(contestId);
        if (contest == null) throw ContestException.notFound();
        requireManage(contest);
        return contest;
    }

    private void requireManage(ContestEntity contest) {
        CurrentUser.requireContentManager();
        if (!CurrentUser.isAdmin() && !(CurrentUser.isTeacher()
                && Objects.equals(CurrentUser.getId(), contest.getOwnerId()))) {
            throw ContestException.forbidden("CONTEST_FORBIDDEN", "无权管理该比赛");
        }
    }

    private void requireRejudgePhase(ContestEntity contest) {
        ContestPhase phase = ContestLifecycle.phase(contest, clock);
        if (phase != ContestPhase.RUNNING && phase != ContestPhase.ENDED) {
            throw ContestException.conflict("REJUDGE_CONTEST_PHASE_INVALID", "仅进行中或已结束比赛可重判");
        }
    }

    private ContestProblemEntity requireContestProblem(int contestId, long contestProblemId) {
        ContestProblemEntity problem = contestProblemMapper.selectById(contestProblemId);
        if (problem == null || !Objects.equals(problem.getContestId(), contestId)) {
            throw ContestException.conflict("PROBLEM_NOT_IN_CONTEST", "题目不属于该比赛");
        }
        return problem;
    }

    private void requireAlgorithm(String problemType) {
        if (!ContestProblemType.ALGORITHM.name().equals(problemType)) {
            throw ContestException.conflict("REJUDGE_UNSUPPORTED_FOR_PROBLEM_TYPE", "当前仅支持算法题重判");
        }
    }

    private void refreshBatchesForSubmission(int submissionId) {
        List<Long> ids = jdbc.queryForList("SELECT DISTINCT batch_id FROM rejudge_batch_item WHERE submission_id = ?",
                Long.class, submissionId);
        ids.forEach(this::refreshBatch);
    }

    private void refreshBatch(long batchId) {
        jdbc.update("""
                UPDATE rejudge_batch batch
                SET queued_count = counts.queued_count,
                    completed_count = counts.completed_count,
                    failed_count = counts.failed_count,
                    status = CASE WHEN counts.queued_count = 0 AND counts.failed_count = 0 THEN 'COMPLETED'
                                  WHEN counts.queued_count = 0 THEN 'FAILED'
                                  ELSE 'RUNNING' END,
                    completed_at = CASE WHEN counts.queued_count = 0 THEN COALESCE(batch.completed_at, NOW()) ELSE NULL END
                FROM (
                    SELECT batch_id,
                           COUNT(*) FILTER (WHERE status = 'QUEUED')::int AS queued_count,
                           COUNT(*) FILTER (WHERE status = 'COMPLETED')::int AS completed_count,
                           COUNT(*) FILTER (WHERE status IN ('FAILED', 'STALE'))::int AS failed_count
                    FROM rejudge_batch_item WHERE batch_id = ? GROUP BY batch_id
                ) counts
                WHERE batch.id = counts.batch_id
                """, batchId);
    }

    private ContestDtos.RejudgeBatch batch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ContestDtos.RejudgeBatch(rs.getLong("id"), rs.getInt("contest_id"),
                rs.getObject("contest_problem_id", Long.class), rs.getObject("requested_submission_id", Integer.class),
                rs.getInt("requested_by"), rs.getString("status"), rs.getInt("total_count"),
                rs.getInt("queued_count"), rs.getInt("completed_count"), rs.getInt("failed_count"),
                instant(rs, "created_at"), instant(rs, "completed_at"));
    }

    private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record SubmissionScope(int submissionId, long contestProblemId, String problemType) {}
}
