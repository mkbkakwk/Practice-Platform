package com.oj.config;

import com.oj.judge.JudgeService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class JudgeConsumerIntegrationTest {

    @Autowired
    private JudgeConsumer judgeConsumer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Queue judgeQueue;

    private JudgeService originalJudgeService;

    @BeforeEach
    void resetDatabase() {
        originalJudgeService = (JudgeService) ReflectionTestUtils.getField(
                judgeConsumer, "judgeService");
        jdbcTemplate.update("DELETE FROM \"Submission\"");
        jdbcTemplate.update("DELETE FROM \"User\"");
    }

    @AfterEach
    void restoreJudgeService() {
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", originalJudgeService);
    }

    @Test
    void deletedSubmissionIsAcknowledgedAndNotRecreated() throws Exception {
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(9999), 41L, channel);

        verify(channel).basicAck(41L, false);
        assertThat(countSubmissions()).isZero();
    }

    @Test
    void deletionDuringJudgingDoesNotRecreateSubmission() throws Exception {
        int userId = insertUser("during_delete");
        int submissionId = insertSubmission(userId, 10, "PENDING");

        JudgeService delayedJudge = mock(JudgeService.class);
        when(delayedJudge.judge(
                eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenAnswer(invocation -> {
                    jdbcTemplate.update(
                            "DELETE FROM \"Submission\" WHERE id=?", submissionId);
                    JudgeService.JudgeResult result = new JudgeService.JudgeResult();
                    result.verdict = "AC";
                    result.passed = 1;
                    result.total = 1;
                    result.message = "ok";
                    return result;
                });
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", delayedJudge);
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(submissionId), 42L, channel);

        verify(channel).basicAck(42L, false);
        assertThat(countSubmissions()).isZero();
        assertThat(solvedCount(userId)).isZero();
    }

    @Test
    void acRecalculatesDistinctSolvedCountAndDuplicateDeliveryDoesNotIncrement() throws Exception {
        int userId = insertUser("repeat_ac");
        jdbcTemplate.update("""
                INSERT INTO "Submission"
                    (user_id, problem_id, language, code, verdict)
                VALUES (?, 20, 'python', 'print(1)', 'AC'),
                       (?, 20, 'python', 'print(1)', 'AC')
                """, userId, userId);
        int submissionId = insertSubmission(userId, 10, "PENDING");
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(submissionId), 43L, channel);
        judgeConsumer.onMessage(payload(submissionId), 44L, channel);

        verify(channel, times(1)).basicAck(43L, false);
        verify(channel, times(1)).basicAck(44L, false);
        assertThat(solvedCount(userId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?",
                String.class, submissionId)).isEqualTo("AC");
    }

    @Test
    void malformedOldMessageIsAcknowledgedInsteadOfRetriedForever() throws Exception {
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(Map.of("unexpected", true), 45L, channel);

        verify(channel).basicAck(45L, false);
        assertThat(countSubmissions()).isZero();
    }

    @Test
    void usesOnlyTheTestQueueName() {
        assertThat(judgeQueue.getName()).isEqualTo("oj.test.judge.queue");
    }

    private int insertUser(String username) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO "User" (username, password, role)
                VALUES (?, 'test', 'USER')
                RETURNING id
                """, Integer.class, username);
    }

    private int insertSubmission(int userId, int problemId, String verdict) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO "Submission"
                    (user_id, problem_id, language, code, verdict)
                VALUES (?, ?, 'python', 'print(1)', ?)
                RETURNING id
                """, Integer.class, userId, problemId, verdict);
    }

    private Map<String, Object> payload(int submissionId) {
        return Map.of(
                "submissionId", submissionId,
                "language", "python",
                "code", "print(1)",
                "timeLimitMs", 1000,
                "memoryLimitKb", 262144,
                "testCasesJson", testCases()
        );
    }

    private String testCases() {
        return "[{\"input\":\"\",\"output\":\"1\"}]";
    }

    private long countSubmissions() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"Submission\"", Long.class);
        return count == null ? 0 : count;
    }

    private int solvedCount(int userId) {
        Integer solved = jdbcTemplate.queryForObject(
                "SELECT solved_count FROM \"User\" WHERE id=?", Integer.class, userId);
        return solved == null ? 0 : solved;
    }
}
