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
    public JudgeClaim claim(int submissionId, UUID judgeToken, Duration lease) {
        int updated = jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'JUDGING', judge_token = ?,
                    judge_lease_until = NOW() + (? * INTERVAL '1 millisecond'),
                    judge_attempt_count = judge_attempt_count + 1,
                    judge_failure_category = NULL,
                    message = '评测中'
                WHERE id = ?
                  AND (verdict = 'PENDING'
                       OR (verdict = 'JUDGING' AND judge_lease_until < NOW()))
                """, judgeToken, lease.toMillis(), submissionId);
        if (updated == 1) {
            return JudgeClaim.claimed(judgeToken, loadWork(submissionId));
        }

        List<String> verdicts = jdbc.query(
                "SELECT verdict FROM \"Submission\" WHERE id = ?",
                (rs, row) -> rs.getString(1), submissionId);
        if (verdicts.isEmpty()) {
            return JudgeClaim.of(JudgeClaim.Status.NOT_FOUND);
        }
        return JudgeClaim.of(FINAL_VERDICTS.contains(verdicts.getFirst())
                ? JudgeClaim.Status.FINAL : JudgeClaim.Status.BUSY);
    }

    @Transactional
    public int complete(int submissionId, UUID judgeToken, JudgeService.JudgeResult result) {
        int updated = jdbc.update("""
                UPDATE "Submission"
                SET verdict = ?, passed = ?, total = ?, time_ms = ?, memory_kb = ?,
                    message = ?, judge_token = NULL, judge_lease_until = NULL,
                    judge_failure_category = NULL
                WHERE id = ? AND verdict = 'JUDGING' AND judge_token = ?
                """, result.verdict, result.passed, result.total,
                (int) Math.min(result.timeMs, Integer.MAX_VALUE),
                (int) Math.min(result.memoryKb, Integer.MAX_VALUE),
                result.message, submissionId, judgeToken);
        if (updated == 1 && "AC".equals(result.verdict)) {
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

    public int releaseForRetry(int submissionId, UUID judgeToken, String category) {
        return jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'PENDING', judge_token = NULL, judge_lease_until = NULL,
                    judge_failure_category = ?, message = '等待评测服务恢复'
                WHERE id = ? AND verdict = 'JUDGING' AND judge_token = ?
                """, category, submissionId, judgeToken);
    }

    public int markFailed(int submissionId, UUID judgeToken, String category) {
        return jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'JUDGE_FAILED', judge_token = NULL, judge_lease_until = NULL,
                    judge_failure_category = ?, message = '评测服务暂时无法完成此提交'
                WHERE id = ? AND verdict = 'JUDGING' AND judge_token = ?
                """, category, submissionId, judgeToken);
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
