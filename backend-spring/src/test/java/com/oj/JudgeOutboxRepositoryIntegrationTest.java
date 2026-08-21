package com.oj;

import com.oj.reliability.JudgeOutboxEvent;
import com.oj.reliability.JudgeOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JudgeOutboxRepositoryIntegrationTest {

    @Autowired
    private JudgeOutboxRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE "judge_outbox", rejudge_batch_item, rejudge_batch, algorithm_judge_history, "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "ContestProblem", "ContestParticipant", "Contest",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        jdbc.update("""
                INSERT INTO "User" (id, username, password, role)
                VALUES (1, 'outbox_repo', 'hash', 'USER')
                """);
        jdbc.update("""
                INSERT INTO "Problem" (id, slug, title, description, test_cases)
                VALUES (1, 'outbox-repo', 'Outbox', 'test', '[]')
                """);
    }

    @Test
    void concurrentPublishersClaimOneHundredEventsWithoutOverlap() throws Exception {
        for (int i = 0; i < 100; i++) {
            int submissionId = insertSubmission();
            UUID eventId = UUID.randomUUID();
            repository.insert(eventId, submissionId, payload(eventId, submissionId));
        }

        List<JudgeOutboxEvent> claimed = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<List<JudgeOutboxEvent>>> tasks = List.of(
                    () -> repository.claimBatch(25, Duration.ofMinutes(1)),
                    () -> repository.claimBatch(25, Duration.ofMinutes(1)),
                    () -> repository.claimBatch(25, Duration.ofMinutes(1)),
                    () -> repository.claimBatch(25, Duration.ofMinutes(1)));
            for (var future : executor.invokeAll(tasks)) {
                claimed.addAll(future.get());
            }
        }

        assertThat(claimed).hasSize(100);
        assertThat(new HashSet<>(claimed.stream().map(JudgeOutboxEvent::eventId).toList()))
                .hasSize(100);
        claimed.forEach(event -> assertThat(repository.markPublished(event)).isEqualTo(1));
        assertThat(repository.pendingCount()).isZero();
    }

    @Test
    void expiredPublisherCannotMutateAReclaimedEvent() {
        int submissionId = insertSubmission();
        UUID eventId = UUID.randomUUID();
        repository.insert(eventId, submissionId, payload(eventId, submissionId));
        JudgeOutboxEvent oldLease = repository.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        jdbc.update("UPDATE judge_outbox SET lease_until=NOW()-INTERVAL '1 second' WHERE id=?",
                oldLease.id());

        JudgeOutboxEvent newLease = repository.claimBatch(1, Duration.ofMinutes(1)).getFirst();

        assertThat(newLease.publisherToken()).isNotEqualTo(oldLease.publisherToken());
        assertThat(repository.markPublished(oldLease)).isZero();
        assertThat(repository.markRetry(oldLease, Duration.ZERO, "STALE")).isZero();
        assertThat(repository.markPublished(newLease)).isEqualTo(1);
    }

    @Test
    void retentionDeletesOnlyOldPublishedRows() {
        int publishedSubmission = insertSubmission();
        UUID publishedEvent = UUID.randomUUID();
        repository.insert(publishedEvent, publishedSubmission,
                payload(publishedEvent, publishedSubmission));
        JudgeOutboxEvent claimed = repository.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(repository.markPublished(claimed)).isEqualTo(1);
        jdbc.update("UPDATE judge_outbox SET published_at=NOW()-INTERVAL '8 days' WHERE id=?", claimed.id());

        int pendingSubmission = insertSubmission();
        UUID pendingEvent = UUID.randomUUID();
        repository.insert(pendingEvent, pendingSubmission, payload(pendingEvent, pendingSubmission));

        assertThat(repository.deletePublishedBefore(Duration.ofDays(7), 100)).isEqualTo(1);
        assertThat(repository.pendingCount()).isEqualTo(1);
    }

    private int insertSubmission() {
        return jdbc.queryForObject("""
                INSERT INTO "Submission" (user_id, problem_id, language, code)
                VALUES (1, 1, 'python', 'print(1)') RETURNING id
                """, Integer.class);
    }

    private String payload(UUID eventId, int submissionId) {
        return "{\"eventId\":\"" + eventId + "\",\"submissionId\":" + submissionId
                + ",\"schemaVersion\":1,\"deliveryAttempt\":0}";
    }
}
