-- Stage 7: derived contest scoring/standings, freeze metadata and generation-safe
-- algorithm rejudge.  Standings remain derived from submission data; no leaderboard
-- truth table is persisted.

ALTER TABLE "Contest"
    ADD COLUMN scoring_mode VARCHAR(10) NOT NULL DEFAULT 'SCORE',
    ADD COLUMN freeze_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_contest_scoring_mode CHECK (scoring_mode IN ('SCORE', 'ICPC')),
    ADD CONSTRAINT ck_contest_freeze_window CHECK (
        freeze_at IS NULL OR (start_at < freeze_at AND freeze_at < end_at)
    );

ALTER TABLE "Submission"
    ADD COLUMN judge_generation INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_submission_judge_generation CHECK (judge_generation >= 0);

ALTER TABLE judge_outbox
    ADD COLUMN judge_generation INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_judge_outbox_generation CHECK (judge_generation >= 0);

ALTER TABLE judge_outbox
    DROP CONSTRAINT uk_judge_outbox_submission_event;

ALTER TABLE judge_outbox
    ADD CONSTRAINT uk_judge_outbox_submission_event_generation
        UNIQUE (submission_id, event_type, judge_generation);

CREATE TABLE algorithm_judge_history (
    id BIGSERIAL PRIMARY KEY,
    submission_id INT NOT NULL REFERENCES "Submission" (id) ON DELETE CASCADE,
    judge_generation INTEGER NOT NULL CHECK (judge_generation >= 0),
    verdict VARCHAR(20) NOT NULL,
    passed INT NOT NULL DEFAULT 0,
    total INT NOT NULL DEFAULT 0,
    time_ms INT NOT NULL DEFAULT 0,
    memory_kb INT NOT NULL DEFAULT 0,
    message TEXT,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_algorithm_judge_history_submission_generation
        UNIQUE (submission_id, judge_generation)
);

CREATE INDEX algorithm_judge_history_submission_generation_idx
    ON algorithm_judge_history (submission_id, judge_generation DESC);

-- Bulk standings scans are ordered by participant/problem/time.  These indexes
-- keep the derived projection bounded without introducing a persisted ranking
-- truth table.
CREATE INDEX submission_contest_standing_idx
    ON "Submission" (contest_problem_id, user_id, created_at, id)
    WHERE contest_problem_id IS NOT NULL;
CREATE INDEX office_record_contest_standing_idx
    ON "OfficeRecord" (contest_problem_id, user_id, created_at, id)
    WHERE contest_problem_id IS NOT NULL;
CREATE INDEX office_doc_submission_contest_standing_idx
    ON "OfficeDocSubmission" (contest_problem_id, user_id, created_at, id)
    WHERE contest_problem_id IS NOT NULL;

-- Existing terminal V8 results are the generation-zero audit record.  Pending,
-- judging and infrastructure-failed rows intentionally have no effective result.
INSERT INTO algorithm_judge_history (
    submission_id, judge_generation, verdict, passed, total, time_ms, memory_kb, message, completed_at
)
SELECT id, 0, verdict, COALESCE(passed, 0), COALESCE(total, 0),
       COALESCE(time_ms, 0), COALESCE(memory_kb, 0), message,
       COALESCE(created_at AT TIME ZONE 'UTC', NOW())
FROM "Submission"
WHERE verdict IN ('AC', 'WA', 'TLE', 'MLE', 'OLE', 'RE', 'CE', 'SE')
ON CONFLICT (submission_id, judge_generation) DO NOTHING;

CREATE TABLE rejudge_batch (
    id BIGSERIAL PRIMARY KEY,
    contest_id INT NOT NULL REFERENCES "Contest" (id) ON DELETE RESTRICT,
    contest_problem_id BIGINT REFERENCES "ContestProblem" (id) ON DELETE RESTRICT,
    requested_submission_id INT REFERENCES "Submission" (id) ON DELETE RESTRICT,
    requested_by INT NOT NULL REFERENCES "User" (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    total_count INT NOT NULL DEFAULT 0,
    queued_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE rejudge_batch_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES rejudge_batch (id) ON DELETE CASCADE,
    submission_id INT NOT NULL REFERENCES "Submission" (id) ON DELETE RESTRICT,
    judge_generation INTEGER NOT NULL CHECK (judge_generation >= 1),
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'COMPLETED', 'FAILED', 'STALE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_rejudge_batch_item_submission_generation
        UNIQUE (submission_id, judge_generation)
);

CREATE INDEX rejudge_batch_contest_created_idx
    ON rejudge_batch (contest_id, created_at DESC);
CREATE INDEX rejudge_batch_item_batch_idx
    ON rejudge_batch_item (batch_id, id);
