package com.oj;

import com.oj.common.CurrentUser;
import com.oj.dto.SubmitRequest;
import com.oj.reliability.JudgeOutboxRepository;
import com.oj.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class SubmissionOutboxIntegrationTest {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private JudgeOutboxRepository outboxRepository;

    @BeforeEach
    void resetDatabase() {
        reset(outboxRepository);
        jdbc.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @AfterEach
    void clearUser() {
        CurrentUser.clear();
        reset(outboxRepository);
    }

    @Test
    void submissionAndMinimalJudgeEventCommitAtomicallyWithoutRabbitAvailability() {
        int userId = insertFixture("outbox_atomic");
        CurrentUser.set(userId, "outbox_atomic", "USER");

        int submissionId = submissionService.submit(request());

        assertThat(jdbc.queryForMap("""
                SELECT verdict, message FROM "Submission" WHERE id=?
                """, submissionId)).containsEntry("verdict", "PENDING")
                .containsEntry("message", "排队中");
        Map<String, Object> outbox = jdbc.queryForMap("""
                SELECT submission_id, status, attempt_count,
                       jsonb_exists(payload, 'eventId') AS has_event_id,
                       jsonb_exists(payload, 'submissionId') AS has_submission_id,
                       jsonb_exists(payload, 'sourceCode') AS has_source,
                       jsonb_exists(payload, 'testCases') AS has_cases
                FROM judge_outbox WHERE submission_id=?
                """, submissionId);
        assertThat(outbox)
                .containsEntry("submission_id", submissionId)
                .containsEntry("status", "PENDING")
                .containsEntry("attempt_count", 0)
                .containsEntry("has_event_id", true)
                .containsEntry("has_submission_id", true)
                .containsEntry("has_source", false)
                .containsEntry("has_cases", false);
    }

    @Test
    void outboxInsertFailureRollsBackSubmissionInsert() {
        int userId = insertFixture("outbox_rollback");
        CurrentUser.set(userId, "outbox_rollback", "USER");
        doThrow(new IllegalStateException("fault injection"))
                .when(outboxRepository).insert(any(), anyInt(), any());

        assertThatThrownBy(() -> submissionService.submit(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fault injection");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"Submission\"", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judge_outbox", Long.class)).isZero();
    }

    private int insertFixture(String username) {
        int userId = jdbc.queryForObject("""
                INSERT INTO "User" (username, password, role)
                VALUES (?, 'hash', 'USER') RETURNING id
                """, Integer.class, username);
        jdbc.update("""
                INSERT INTO "Problem"
                    (id, slug, title, description, test_cases, visible)
                VALUES (10, 'outbox-problem', 'Outbox problem', 'test',
                        '[{"input":"","output":"1"}]', TRUE)
                """);
        return userId;
    }

    private SubmitRequest request() {
        SubmitRequest request = new SubmitRequest();
        request.setProblemId(10);
        request.setLanguage("python");
        request.setCode("print(1)");
        return request;
    }
}
