-- Stage 6: contest lifecycle, membership, problem composition, and immutable
-- submission context. Ranking and scoring intentionally remain out of scope.

ALTER TABLE "Problem"
    ADD COLUMN content_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD CONSTRAINT ck_problem_content_visibility
        CHECK (content_visibility IN ('PUBLIC', 'CONTEST_ONLY'));

ALTER TABLE "OfficeExercise"
    ADD COLUMN content_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD CONSTRAINT ck_office_exercise_content_visibility
        CHECK (content_visibility IN ('PUBLIC', 'CONTEST_ONLY'));

CREATE TABLE "Contest" (
    id          SERIAL CONSTRAINT pk_contest PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    access_type VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    owner_id    INT NOT NULL,
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_contest_owner FOREIGN KEY (owner_id)
        REFERENCES "User" (id) ON DELETE RESTRICT,
    CONSTRAINT ck_contest_title_nonblank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_contest_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT ck_contest_access_type CHECK (access_type IN ('OPEN', 'INVITE_ONLY')),
    CONSTRAINT ck_contest_time_window CHECK (end_at > start_at)
);

CREATE TABLE "ContestParticipant" (
    id          BIGSERIAL CONSTRAINT pk_contest_participant PRIMARY KEY,
    contest_id  INT NOT NULL,
    user_id     INT NOT NULL,
    added_by    INT,
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_contest_participant_contest FOREIGN KEY (contest_id)
        REFERENCES "Contest" (id) ON DELETE CASCADE,
    CONSTRAINT fk_contest_participant_user FOREIGN KEY (user_id)
        REFERENCES "User" (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contest_participant_added_by FOREIGN KEY (added_by)
        REFERENCES "User" (id) ON DELETE SET NULL,
    CONSTRAINT uk_contest_participant UNIQUE (contest_id, user_id)
);

CREATE TABLE "ContestProblem" (
    id                  BIGSERIAL CONSTRAINT pk_contest_problem PRIMARY KEY,
    contest_id          INT NOT NULL,
    problem_type        VARCHAR(16) NOT NULL,
    algorithm_problem_id INT,
    office_exercise_id  INT,
    display_order       INT NOT NULL,
    label               VARCHAR(40),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_contest_problem_contest FOREIGN KEY (contest_id)
        REFERENCES "Contest" (id) ON DELETE CASCADE,
    CONSTRAINT fk_contest_problem_algorithm FOREIGN KEY (algorithm_problem_id)
        REFERENCES "Problem" (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contest_problem_office FOREIGN KEY (office_exercise_id)
        REFERENCES "OfficeExercise" (id) ON DELETE RESTRICT,
    CONSTRAINT ck_contest_problem_type CHECK (problem_type IN ('ALGORITHM', 'OFFICE')),
    CONSTRAINT ck_contest_problem_target CHECK (
        (problem_type = 'ALGORITHM' AND algorithm_problem_id IS NOT NULL AND office_exercise_id IS NULL)
        OR
        (problem_type = 'OFFICE' AND office_exercise_id IS NOT NULL AND algorithm_problem_id IS NULL)
    ),
    CONSTRAINT ck_contest_problem_order CHECK (display_order > 0),
    CONSTRAINT uk_contest_problem_order UNIQUE (contest_id, display_order),
    CONSTRAINT uk_contest_problem_algorithm_context UNIQUE (id, algorithm_problem_id),
    CONSTRAINT uk_contest_problem_office_context UNIQUE (id, office_exercise_id)
);

CREATE UNIQUE INDEX "ContestProblem_algorithm_unique_idx"
    ON "ContestProblem" (contest_id, algorithm_problem_id)
    WHERE problem_type = 'ALGORITHM';
CREATE UNIQUE INDEX "ContestProblem_office_unique_idx"
    ON "ContestProblem" (contest_id, office_exercise_id)
    WHERE problem_type = 'OFFICE';
CREATE INDEX "Contest_status_time_idx" ON "Contest" (status, start_at, end_at);
CREATE INDEX "Contest_owner_idx" ON "Contest" (owner_id, id DESC);
CREATE INDEX "ContestParticipant_user_idx" ON "ContestParticipant" (user_id, contest_id);
CREATE INDEX "ContestProblem_contest_order_idx" ON "ContestProblem" (contest_id, display_order);

ALTER TABLE "Submission" ADD COLUMN contest_problem_id BIGINT;
ALTER TABLE "Submission"
    ADD CONSTRAINT fk_submission_contest_problem_context
        FOREIGN KEY (contest_problem_id, problem_id)
        REFERENCES "ContestProblem" (id, algorithm_problem_id) ON DELETE RESTRICT;
CREATE INDEX "Submission_contest_problem_idx"
    ON "Submission" (contest_problem_id, created_at DESC)
    WHERE contest_problem_id IS NOT NULL;

ALTER TABLE "OfficeDocSubmission" ADD COLUMN contest_problem_id BIGINT;
ALTER TABLE "OfficeDocSubmission"
    ADD CONSTRAINT fk_office_submission_contest_problem_context
        FOREIGN KEY (contest_problem_id, exercise_id)
        REFERENCES "ContestProblem" (id, office_exercise_id) ON DELETE RESTRICT;
CREATE INDEX "OfficeDocSubmission_contest_problem_idx"
    ON "OfficeDocSubmission" (contest_problem_id, created_at DESC)
    WHERE contest_problem_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_contest_submission_context_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.contest_problem_id IS DISTINCT FROM OLD.contest_problem_id THEN
        RAISE EXCEPTION 'contest submission context is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_submission_contest_context_immutable
    BEFORE UPDATE OF contest_problem_id ON "Submission"
    FOR EACH ROW EXECUTE FUNCTION prevent_contest_submission_context_change();

CREATE TRIGGER trg_office_submission_contest_context_immutable
    BEFORE UPDATE OF contest_problem_id ON "OfficeDocSubmission"
    FOR EACH ROW EXECUTE FUNCTION prevent_contest_submission_context_change();
