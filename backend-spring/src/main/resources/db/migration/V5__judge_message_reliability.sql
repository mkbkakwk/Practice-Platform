-- Stage 4: at-least-once judge delivery with a transactional outbox and
-- database-backed Worker ownership. PostgreSQL remains the source of truth.

ALTER TABLE "Submission"
    ADD COLUMN judge_token UUID,
    ADD COLUMN judge_lease_until TIMESTAMP,
    ADD COLUMN judge_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN judge_failure_category VARCHAR(64);

ALTER TABLE "Submission" DROP CONSTRAINT ck_submission_verdict;
ALTER TABLE "Submission"
    ADD CONSTRAINT ck_submission_verdict CHECK (verdict IN (
        'PENDING', 'JUDGING', 'JUDGE_FAILED',
        'AC', 'WA', 'TLE', 'MLE', 'OLE', 'RE', 'CE', 'SE'
    )),
    ADD CONSTRAINT ck_submission_judge_attempt_count
        CHECK (judge_attempt_count >= 0),
    ADD CONSTRAINT ck_submission_judging_lease CHECK (
        (verdict = 'JUDGING' AND judge_token IS NOT NULL AND judge_lease_until IS NOT NULL)
        OR
        (verdict <> 'JUDGING' AND judge_token IS NULL AND judge_lease_until IS NULL)
    );

CREATE INDEX "Submission_judging_lease_idx"
    ON "Submission" (judge_lease_until)
    WHERE verdict = 'JUDGING';

CREATE TABLE judge_outbox (
    id              BIGSERIAL CONSTRAINT pk_judge_outbox PRIMARY KEY,
    event_id        UUID CONSTRAINT uk_judge_outbox_event_id UNIQUE NOT NULL,
    event_type      VARCHAR(40) NOT NULL,
    submission_id   INT NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count   INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    locked_at       TIMESTAMP,
    lease_until     TIMESTAMP,
    publisher_token UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,
    last_error      VARCHAR(128),
    CONSTRAINT fk_judge_outbox_submission
        FOREIGN KEY (submission_id) REFERENCES "Submission" (id) ON DELETE CASCADE,
    CONSTRAINT uk_judge_outbox_submission_event
        UNIQUE (submission_id, event_type),
    CONSTRAINT ck_judge_outbox_event_type
        CHECK (event_type = 'JUDGE_REQUESTED'),
    CONSTRAINT ck_judge_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED')),
    CONSTRAINT ck_judge_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_judge_outbox_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_judge_outbox_publishing_lease CHECK (
        (status = 'PUBLISHING' AND locked_at IS NOT NULL
            AND lease_until IS NOT NULL AND publisher_token IS NOT NULL)
        OR
        (status <> 'PUBLISHING' AND publisher_token IS NULL)
    ),
    CONSTRAINT ck_judge_outbox_published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR status <> 'PUBLISHED'
    )
);

CREATE INDEX "judge_outbox_ready_idx"
    ON judge_outbox (status, next_attempt_at, created_at);
CREATE INDEX "judge_outbox_submission_idx"
    ON judge_outbox (submission_id);
