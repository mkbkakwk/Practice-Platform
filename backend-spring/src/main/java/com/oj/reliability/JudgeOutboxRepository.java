package com.oj.reliability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
public class JudgeOutboxRepository {

    private final JdbcTemplate jdbc;

    public JudgeOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID eventId, int submissionId, String payload) {
        jdbc.update("""
                INSERT INTO judge_outbox (event_id, event_type, submission_id, payload)
                VALUES (?, 'JUDGE_REQUESTED', ?, ?::jsonb)
                """, eventId, submissionId, payload);
    }

    @Transactional
    public List<JudgeOutboxEvent> claimBatch(int batchSize, Duration lease) {
        UUID publisherToken = UUID.randomUUID();
        return jdbc.query("""
                WITH candidates AS (
                    SELECT id
                    FROM judge_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= NOW())
                       OR (status = 'PUBLISHING' AND lease_until < NOW())
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE judge_outbox outbox
                SET status = 'PUBLISHING',
                    attempt_count = outbox.attempt_count + 1,
                    locked_at = NOW(),
                    lease_until = NOW() + (? * INTERVAL '1 millisecond'),
                    publisher_token = ?,
                    last_error = NULL
                FROM candidates
                WHERE outbox.id = candidates.id
                RETURNING outbox.id, outbox.event_id, outbox.submission_id,
                          outbox.payload::text, outbox.attempt_count
                """, (rs, row) -> new JudgeOutboxEvent(
                rs.getLong("id"),
                rs.getObject("event_id", UUID.class),
                rs.getInt("submission_id"),
                rs.getString("payload"),
                rs.getInt("attempt_count"),
                publisherToken), batchSize, lease.toMillis(), publisherToken);
    }

    public int markPublished(JudgeOutboxEvent event) {
        return jdbc.update("""
                UPDATE judge_outbox
                SET status = 'PUBLISHED', published_at = NOW(),
                    locked_at = NULL, lease_until = NULL, publisher_token = NULL,
                    last_error = NULL
                WHERE id = ? AND event_id = ? AND status = 'PUBLISHING'
                  AND publisher_token = ?
                """, event.id(), event.eventId(), event.publisherToken());
    }

    public int markRetry(JudgeOutboxEvent event, Duration delay, String failureCategory) {
        return jdbc.update("""
                UPDATE judge_outbox
                SET status = 'PENDING',
                    next_attempt_at = NOW() + (? * INTERVAL '1 millisecond'),
                    locked_at = NULL, lease_until = NULL, publisher_token = NULL,
                    last_error = ?
                WHERE id = ? AND event_id = ? AND status = 'PUBLISHING'
                  AND publisher_token = ?
                """, delay.toMillis(), failureCategory, event.id(), event.eventId(),
                event.publisherToken());
    }

    public int deletePublishedBefore(Duration retention, int batchSize) {
        return jdbc.update("""
                DELETE FROM judge_outbox
                WHERE id IN (
                    SELECT id FROM judge_outbox
                    WHERE status = 'PUBLISHED'
                      AND published_at < NOW() - (? * INTERVAL '1 millisecond')
                    ORDER BY published_at
                    LIMIT ?
                )
                """, retention.toMillis(), batchSize);
    }

    public long pendingCount() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM judge_outbox WHERE status <> 'PUBLISHED'
                """, Long.class);
        return count == null ? 0 : count;
    }
}
