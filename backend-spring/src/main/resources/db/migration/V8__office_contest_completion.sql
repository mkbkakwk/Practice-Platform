-- Stage 6.6: complete Office contest support without introducing scoring or
-- ranking semantics. Existing OFFICE contest rows are DOCX exercises.

ALTER TABLE "OfficeQuestion"
    ADD COLUMN content_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD CONSTRAINT ck_office_question_content_visibility
        CHECK (content_visibility IN ('PUBLIC', 'CONTEST_ONLY'));

ALTER TABLE "OfficeExercise"
    ADD COLUMN starter_doc_path VARCHAR(255),
    ADD COLUMN starter_doc_name VARCHAR(255);

ALTER TABLE "ContestProblem"
    ADD COLUMN office_question_id INT;

ALTER TABLE "ContestProblem"
    DROP CONSTRAINT ck_contest_problem_type,
    DROP CONSTRAINT ck_contest_problem_target;

UPDATE "ContestProblem"
SET problem_type = 'OFFICE_DOCX'
WHERE problem_type = 'OFFICE';

ALTER TABLE "ContestProblem"
    ADD CONSTRAINT fk_contest_problem_office_question FOREIGN KEY (office_question_id)
        REFERENCES "OfficeQuestion" (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_contest_problem_type
        CHECK (problem_type IN ('ALGORITHM', 'OFFICE_CHOICE', 'OFFICE_DOCX')),
    ADD CONSTRAINT ck_contest_problem_target CHECK (
        (problem_type = 'ALGORITHM'
            AND algorithm_problem_id IS NOT NULL
            AND office_question_id IS NULL
            AND office_exercise_id IS NULL)
        OR
        (problem_type = 'OFFICE_CHOICE'
            AND office_question_id IS NOT NULL
            AND algorithm_problem_id IS NULL
            AND office_exercise_id IS NULL)
        OR
        (problem_type = 'OFFICE_DOCX'
            AND office_exercise_id IS NOT NULL
            AND algorithm_problem_id IS NULL
            AND office_question_id IS NULL)
    ),
    ADD CONSTRAINT uk_contest_problem_choice_context UNIQUE (id, office_question_id);

DROP INDEX "ContestProblem_office_unique_idx";

CREATE UNIQUE INDEX "ContestProblem_choice_unique_idx"
    ON "ContestProblem" (contest_id, office_question_id)
    WHERE problem_type = 'OFFICE_CHOICE';

CREATE UNIQUE INDEX "ContestProblem_docx_unique_idx"
    ON "ContestProblem" (contest_id, office_exercise_id)
    WHERE problem_type = 'OFFICE_DOCX';

ALTER TABLE "OfficeRecord"
    ADD COLUMN contest_problem_id BIGINT,
    ADD CONSTRAINT fk_office_record_contest_problem_context
        FOREIGN KEY (contest_problem_id, question_id)
        REFERENCES "ContestProblem" (id, office_question_id) ON DELETE RESTRICT;

CREATE INDEX "OfficeRecord_contest_problem_idx"
    ON "OfficeRecord" (contest_problem_id, created_at DESC)
    WHERE contest_problem_id IS NOT NULL;

CREATE TRIGGER trg_office_record_contest_context_immutable
    BEFORE UPDATE OF contest_problem_id ON "OfficeRecord"
    FOR EACH ROW EXECUTE FUNCTION prevent_contest_submission_context_change();
