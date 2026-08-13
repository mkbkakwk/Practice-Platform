package com.oj.config;

import com.oj.judge.JudgeService;
import com.oj.reliability.JudgeMessageRouter;
import com.oj.reliability.JudgeSubmissionRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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

    @Autowired
    private JudgeSubmissionRepository submissions;

    @MockBean
    private JudgeMessageRouter router;

    private JudgeService originalJudgeService;

    @BeforeEach
    void resetDatabase() {
        reset(router);
        when(router.retry(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(true);
        when(router.deadLetter(org.mockito.ArgumentMatchers.any(), anyInt(), anyString())).thenReturn(true);
        when(router.malformed(anyString(), anyInt())).thenReturn(true);
        originalJudgeService = (JudgeService) ReflectionTestUtils.getField(
                judgeConsumer, "judgeService");
        jdbcTemplate.update("DELETE FROM \"Submission\"");
        jdbcTemplate.update("DELETE FROM \"Problem\"");
        jdbcTemplate.update("DELETE FROM \"User\"");
        jdbcTemplate.update("""
                INSERT INTO "Problem" (id, slug, title, description, test_cases)
                VALUES (10, 'worker-problem-10', 'Worker problem 10', 'test',
                        '[{"input":"","output":"1"}]'),
                       (20, 'worker-problem-20', 'Worker problem 20', 'test',
                        '[{"input":"","output":"1"}]')
                """);
    }

    @AfterEach
    void restoreJudgeService() {
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", originalJudgeService);
    }

    @Test
    void deletedSubmissionWithEmptyTestCasesIsAcknowledgedAndNotRecreated() throws Exception {
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(9999, "[]"), 41L, null, channel);

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

        judgeConsumer.onMessage(payload(submissionId), 42L, null, channel);

        verify(channel).basicAck(42L, false);
        assertThat(countSubmissions()).isZero();
        assertThat(solvedCount(userId)).isZero();
    }

    @Test
    void emptyTestCasesAreDeadLetteredAndDoNotIncreaseSolvedCount() throws Exception {
        int userId = insertUser("empty_cases");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        jdbcTemplate.update("UPDATE \"Problem\" SET test_cases='[]' WHERE id=10");
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(submissionId, "[]"), 46L, null, channel);

        verify(channel).basicAck(46L, false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?",
                String.class, submissionId)).isEqualTo("JUDGE_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM \"Submission\" WHERE id=?",
                String.class, submissionId)).isEqualTo("评测服务暂时无法完成此提交");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT judge_failure_category FROM \"Submission\" WHERE id=?",
                String.class, submissionId)).isEqualTo("INVALID_PROBLEM_CONFIGURATION");
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
        JudgeService oneExecution = mock(JudgeService.class);
        JudgeService.JudgeResult ac = result("AC");
        when(oneExecution.judge(eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenReturn(ac);
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", oneExecution);
        Channel channel = mock(Channel.class);

        for (long tag = 43; tag < 48; tag++) {
            judgeConsumer.onMessage(payload(submissionId), tag, null, channel);
        }

        verify(channel, times(5)).basicAck(anyLong(), eq(false));
        verify(oneExecution, times(1)).judge(
                eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases()));
        assertThat(solvedCount(userId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?",
                String.class, submissionId)).isEqualTo("AC");
    }

    @Test
    void malformedOldMessageIsAcknowledgedInsteadOfRetriedForever() throws Exception {
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(Map.<String, Object>of("unexpected", true), 45L, null, channel);

        verify(channel).basicAck(45L, false);
        assertThat(countSubmissions()).isZero();
    }

    @Test
    void invalidJsonIsDeadLetteredThroughTheRawListenerBoundary() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(49L);
        Message invalid = new Message("{not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);
        Channel channel = mock(Channel.class);

        judgeConsumer.onRabbitMessage(invalid, channel);

        verify(router).malformed("INVALID_MESSAGE", 0);
        verify(channel).basicAck(49L, false);
    }

    @Test
    void activeLeaseIsDelayedWithoutASecondExecution() throws Exception {
        int userId = insertUser("active_lease");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        jdbcTemplate.update("""
                UPDATE "Submission"
                SET verdict='JUDGING', judge_token=?, judge_lease_until=NOW()+INTERVAL '10 minutes'
                WHERE id=?
                """, UUID.randomUUID(), submissionId);
        JudgeService judge = mock(JudgeService.class);
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", judge);
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(submissionId), 50L, null, channel);

        verify(router).retry(org.mockito.ArgumentMatchers.any(), eq("LEASE_BUSY"));
        verify(channel).basicAck(50L, false);
        verify(judge, never()).judge(anyString(), anyString(), anyLong(), anyLong(), anyString());
    }

    @Test
    void expiredLeaseCanBeRecoveredByANewJudgeToken() throws Exception {
        int userId = insertUser("expired_lease");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        UUID expiredToken = UUID.randomUUID();
        jdbcTemplate.update("""
                UPDATE "Submission"
                SET verdict='JUDGING', judge_token=?, judge_lease_until=NOW()-INTERVAL '1 second'
                WHERE id=?
                """, expiredToken, submissionId);
        JudgeService judge = mock(JudgeService.class);
        when(judge.judge(eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenReturn(result("AC"));
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", judge);
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(submissionId), 51L, null, channel);

        verify(channel).basicAck(51L, false);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT verdict, judge_token, judge_lease_until FROM "Submission" WHERE id=?
                """, submissionId)).containsEntry("verdict", "AC")
                .containsEntry("judge_token", null)
                .containsEntry("judge_lease_until", null);
    }

    @Test
    void workerCrashAfterClaimIsRecoveredAfterLeaseExpiry() throws Exception {
        int userId = insertUser("claim_crash");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        JudgeService crashedWorker = mock(JudgeService.class);
        when(crashedWorker.judge(eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenThrow(new AssertionError("simulated process death"));
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", crashedWorker);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> judgeConsumer.onMessage(payload(submissionId), 56L, null, mock(Channel.class)))
                .isInstanceOf(AssertionError.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?", String.class, submissionId))
                .isEqualTo("JUDGING");
        jdbcTemplate.update("""
                UPDATE "Submission" SET judge_lease_until=NOW()-INTERVAL '1 second' WHERE id=?
                """, submissionId);

        JudgeService replacementWorker = mock(JudgeService.class);
        when(replacementWorker.judge(eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenReturn(result("AC"));
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", replacementWorker);
        Channel redelivery = mock(Channel.class);
        judgeConsumer.onMessage(payload(submissionId), 57L, null, redelivery);

        verify(redelivery).basicAck(57L, false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?", String.class, submissionId))
                .isEqualTo("AC");
    }

    @Test
    void expiredWorkerTokenCannotOverwriteRecoveredResult() {
        int userId = insertUser("stale_token");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        UUID oldToken = UUID.randomUUID();
        assertThat(submissions.claim(submissionId, oldToken, java.time.Duration.ofMinutes(10)).status())
                .isEqualTo(com.oj.reliability.JudgeClaim.Status.CLAIMED);
        jdbcTemplate.update("""
                UPDATE "Submission" SET judge_lease_until=NOW()-INTERVAL '1 second' WHERE id=?
                """, submissionId);
        UUID newToken = UUID.randomUUID();
        assertThat(submissions.claim(submissionId, newToken, java.time.Duration.ofMinutes(10)).status())
                .isEqualTo(com.oj.reliability.JudgeClaim.Status.CLAIMED);

        assertThat(submissions.complete(submissionId, oldToken, result("WA"))).isZero();
        assertThat(submissions.complete(submissionId, newToken, result("AC"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?", String.class, submissionId))
                .isEqualTo("AC");
    }

    @Test
    void resultCommittedBeforeLostAckMakesRedeliveryAnIdempotentSuccess() throws Exception {
        int userId = insertUser("lost_ack");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        JudgeService judge = mock(JudgeService.class);
        when(judge.judge(eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenReturn(result("AC"));
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", judge);
        Channel lostAck = mock(Channel.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("ack lost"))
                .when(lostAck).basicAck(52L, false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> judgeConsumer.onMessage(payload(submissionId), 52L, null, lostAck))
                .isInstanceOf(java.io.IOException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?", String.class, submissionId))
                .isEqualTo("AC");

        Channel redelivery = mock(Channel.class);
        judgeConsumer.onMessage(payload(submissionId), 53L, null, redelivery);

        verify(redelivery).basicAck(53L, false);
        verify(judge, times(1)).judge(
                eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases()));
    }

    @Test
    void runnerFailureRetriesThenMovesToDlqAndJudgeFailed() throws Exception {
        int userId = insertUser("runner_down");
        int submissionId = insertSubmission(userId, 10, "PENDING");
        JudgeService judge = mock(JudgeService.class);
        when(judge.judge(eq("python"), eq("print(1)"), anyLong(), anyLong(), eq(testCases())))
                .thenReturn(result("SE"));
        ReflectionTestUtils.setField(judgeConsumer, "judgeService", judge);
        Channel channel = mock(Channel.class);

        judgeConsumer.onMessage(payload(submissionId, 0), 54L, null, channel);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verdict FROM \"Submission\" WHERE id=?", String.class, submissionId))
                .isEqualTo("PENDING");
        verify(router).retry(org.mockito.ArgumentMatchers.any(), eq("RUNNER_UNAVAILABLE"));

        judgeConsumer.onMessage(payload(submissionId, 2), 55L, null, channel);

        verify(router).deadLetter(org.mockito.ArgumentMatchers.any(), eq(3), eq("RUNNER_UNAVAILABLE"));
        assertThat(jdbcTemplate.queryForMap("""
                SELECT verdict, judge_failure_category FROM "Submission" WHERE id=?
                """, submissionId)).containsEntry("verdict", "JUDGE_FAILED")
                .containsEntry("judge_failure_category", "RUNNER_UNAVAILABLE");
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
        return payload(submissionId, 0);
    }

    private Map<String, Object> payload(int submissionId, String testCasesJson) {
        return Map.of(
                "submissionId", submissionId,
                "language", "python",
                "code", "print(1)",
                "timeLimitMs", 1000,
                "memoryLimitKb", 262144,
                "testCasesJson", testCasesJson
        );
    }

    private Map<String, Object> payload(int submissionId, int deliveryAttempt) {
        return Map.of(
                "eventId", UUID.nameUUIDFromBytes(("event-" + submissionId).getBytes()).toString(),
                "submissionId", submissionId,
                "schemaVersion", 1,
                "deliveryAttempt", deliveryAttempt);
    }

    private JudgeService.JudgeResult result(String verdict) {
        JudgeService.JudgeResult result = new JudgeService.JudgeResult();
        result.verdict = verdict;
        result.passed = "AC".equals(verdict) ? 1 : 0;
        result.total = 1;
        result.message = verdict;
        result.requestId = UUID.randomUUID().toString();
        return result;
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
