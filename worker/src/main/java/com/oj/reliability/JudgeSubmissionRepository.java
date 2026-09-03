package com.oj.reliability;

import com.oj.judge.JudgeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
public class JudgeSubmissionRepository {

    private static final List<String> FINAL_VERDICTS = List.of(
            "AC", "WA", "TLE", "MLE", "OLE", "RE", "CE", "SE", "JUDGE_FAILED");

    private final JdbcTemplate jdbc;

    public JudgeSubmissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public JudgeClaim claim(int submissionId, int judgeGeneration, UUID judgeToken, Duration lease) {
        int updated = jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'JUDGING', judge_token = ?,
                    judge_lease_until = NOW() + (? * INTERVAL '1 millisecond'),
                    judge_attempt_count = judge_attempt_count + 1,
                    judge_failure_category = NULL,
                    message = '评测中'
                WHERE id = ? AND judge_generation = ?
                  AND (verdict = 'PENDING'
                       OR (verdict = 'JUDGING' AND judge_lease_until < NOW()))
                """, judgeToken, lease.toMillis(), submissionId, judgeGeneration);
        if (updated == 1) {
            return JudgeClaim.claimed(judgeToken, loadWork(submissionId));
        }

        List<Object[]> verdicts = jdbc.query(
                "SELECT verdict, judge_generation FROM \"Submission\" WHERE id = ?",
                (rs, row) -> new Object[] { rs.getString(1), rs.getInt(2) }, submissionId);
        if (verdicts.isEmpty()) {
            return JudgeClaim.of(JudgeClaim.Status.NOT_FOUND);
        }
        Object[] current = (Object[]) verdicts.getFirst();
        if (((Integer) current[1]) != judgeGeneration) {
            return JudgeClaim.of(JudgeClaim.Status.STALE);
        }
        return JudgeClaim.of(FINAL_VERDICTS.contains((String) current[0])
                ? JudgeClaim.Status.FINAL : JudgeClaim.Status.BUSY);
    }

    /** Existing delivery tests and pre-V9 messages operate on generation zero. */
    public JudgeClaim claim(int submissionId, UUID judgeToken, Duration lease) {
        return claim(submissionId, 0, judgeToken, lease);
    }

    @Transactional
    public int complete(int submissionId, int judgeGeneration, UUID judgeToken, JudgeService.JudgeResult result) {
        int updated = jdbc.update("""
                UPDATE "Submission"
                SET verdict = ?, passed = ?, total = ?, time_ms = ?, memory_kb = ?,
                    message = ?, judge_token = NULL, judge_lease_until = NULL,
                    judge_failure_category = NULL
                WHERE id = ? AND judge_generation = ? AND verdict = 'JUDGING' AND judge_token = ?
                """, result.verdict, result.passed, result.total,
                (int) Math.min(result.timeMs, Integer.MAX_VALUE),
                (int) Math.min(result.memoryKb, Integer.MAX_VALUE),
                result.message, submissionId, judgeGeneration, judgeToken);
        if (updated == 1) {
            jdbc.update("""
                    INSERT INTO algorithm_judge_history
                        (submission_id, judge_generation, verdict, passed, total, time_ms, memory_kb, message)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (submission_id, judge_generation) DO NOTHING
                    """, submissionId, judgeGeneration, result.verdict, result.passed, result.total,
                    (int) Math.min(result.timeMs, Integer.MAX_VALUE),
                    (int) Math.min(result.memoryKb, Integer.MAX_VALUE), result.message);
            completeRejudgeItem(submissionId, judgeGeneration, "COMPLETED");
        }
        if (updated == 1) {
            jdbc.update("""
                    UPDATE "User" user_row
                    SET solved_count = (
                        SELECT COUNT(DISTINCT problem_id)
                        FROM "Submission"
                        WHERE user_id = user_row.id AND verdict = 'AC'
                    )
                    WHERE id = (SELECT user_id FROM "Submission" WHERE id = ?)
                """, submissionId);
        }
        return updated;
    }

    public int complete(int submissionId, UUID judgeToken, JudgeService.JudgeResult result) {
        return complete(submissionId, 0, judgeToken, result);
    }

    public int releaseForRetry(int submissionId, int judgeGeneration, UUID judgeToken, String category) {
        int updated = jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'PENDING', judge_token = NULL, judge_lease_until = NULL,
                    judge_failure_category = ?, message = '等待评测服务恢复'
                WHERE id = ? AND judge_generation = ? AND verdict = 'JUDGING' AND judge_token = ?
                """, category, submissionId, judgeGeneration, judgeToken);
        return updated;
    }

    @Transactional
    public int markFailed(int submissionId, int judgeGeneration, UUID judgeToken, String category) {
        int updated = jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'JUDGE_FAILED', judge_token = NULL, judge_lease_until = NULL,
                    judge_failure_category = ?, message = '评测服务暂时无法完成此提交'
                WHERE id = ? AND judge_generation = ? AND verdict = 'JUDGING' AND judge_token = ?
                """, category, submissionId, judgeGeneration, judgeToken);
        if (updated == 1) {
            jdbc.update("""
                    INSERT INTO algorithm_judge_history
                        (submission_id, judge_generation, verdict, passed, total, time_ms, memory_kb, message)
                    SELECT id, judge_generation, verdict, COALESCE(passed, 0), COALESCE(total, 0),
                           COALESCE(time_ms, 0), COALESCE(memory_kb, 0), message
                    FROM "Submission" WHERE id = ? AND judge_generation = ?
                    ON CONFLICT (submission_id, judge_generation) DO NOTHING
                    """, submissionId, judgeGeneration);
            completeRejudgeItem(submissionId, judgeGeneration, "FAILED");
        }
        return updated;
    }

    private void completeRejudgeItem(int submissionId, int judgeGeneration, String status) {
        jdbc.update("""
                UPDATE rejudge_batch_item
                SET status = ?, completed_at = NOW()
                WHERE submission_id = ? AND judge_generation = ? AND status = 'QUEUED'
                """, status, submissionId, judgeGeneration);
        jdbc.update("""
                UPDATE rejudge_batch batch
                SET queued_count = counts.queued_count,
                    completed_count = counts.completed_count,
                    failed_count = counts.failed_count,
                    status = CASE WHEN counts.queued_count = 0 AND counts.failed_count = 0 THEN 'COMPLETED'
                                  WHEN counts.queued_count = 0 THEN 'FAILED'
                                  ELSE 'RUNNING' END,
                    completed_at = CASE WHEN counts.queued_count = 0 THEN NOW() ELSE NULL END
                FROM (
                    SELECT item.batch_id,
                           COUNT(*) FILTER (WHERE item.status = 'QUEUED')::int AS queued_count,
                           COUNT(*) FILTER (WHERE item.status = 'COMPLETED')::int AS completed_count,
                           COUNT(*) FILTER (WHERE item.status IN ('FAILED', 'STALE'))::int AS failed_count
                    FROM rejudge_batch_item item
                    WHERE item.batch_id IN (
                        SELECT batch_id FROM rejudge_batch_item
                        WHERE submission_id = ? AND judge_generation = ?
                    )
                    GROUP BY item.batch_id
                ) counts
                WHERE batch.id = counts.batch_id
                """, submissionId, judgeGeneration);
    }

    private JudgeWork loadWork(int submissionId) {
        return jdbc.queryForObject("""
                SELECT submission.id, submission.user_id, submission.language, submission.code,
                       problem.time_limit, problem.memory_limit, problem.test_cases
                FROM "Submission" submission
                JOIN "Problem" problem ON problem.id = submission.problem_id
                WHERE submission.id = ?
                """, (rs, row) -> new JudgeWork(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("language"),
                rs.getString("code"),
                rs.getLong("time_limit"),
                Math.multiplyExact(rs.getLong("memory_limit"), 1024L),
                rs.getString("test_cases")), submissionId);
    }
}
