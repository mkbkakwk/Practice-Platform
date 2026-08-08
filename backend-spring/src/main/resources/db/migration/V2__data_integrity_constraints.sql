-- Refuse to add relational constraints while legacy orphan rows exist.
-- Migrations deliberately fail instead of deleting or rewriting real data.
DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM "Submission" child LEFT JOIN "User" parent ON parent.id = child.user_id
    WHERE parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: Submission.user_id has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "Submission" child LEFT JOIN "Problem" parent ON parent.id = child.problem_id
    WHERE parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: Submission.problem_id has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "OfficeRecord" child LEFT JOIN "User" parent ON parent.id = child.user_id
    WHERE parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: OfficeRecord.user_id has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "OfficeRecord" child LEFT JOIN "OfficeQuestion" parent ON parent.id = child.question_id
    WHERE parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: OfficeRecord.question_id has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "OfficeDocSubmission" child LEFT JOIN "User" parent ON parent.id = child.user_id
    WHERE parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: OfficeDocSubmission.user_id has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "OfficeDocSubmission" child LEFT JOIN "OfficeExercise" parent ON parent.id = child.exercise_id
    WHERE parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: OfficeDocSubmission.exercise_id has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "Problem" child LEFT JOIN "User" parent ON parent.id = child.created_by
    WHERE child.created_by IS NOT NULL AND parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: Problem.created_by has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "OfficeQuestion" child LEFT JOIN "User" parent ON parent.id = child.created_by
    WHERE child.created_by IS NOT NULL AND parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: OfficeQuestion.created_by has % orphan row(s)', orphan_count;
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM "OfficeExercise" child LEFT JOIN "User" parent ON parent.id = child.created_by
    WHERE child.created_by IS NOT NULL AND parent.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Orphan check failed: OfficeExercise.created_by has % orphan row(s)', orphan_count;
    END IF;
END $$;

-- Replace any legacy single-column foreign keys on the target associations so
-- fresh and upgraded databases use the same canonical constraint names.
DO $$
DECLARE
    target RECORD;
    existing_fk RECORD;
    target_table REGCLASS;
    target_column SMALLINT;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('Submission', 'user_id'),
            ('Submission', 'problem_id'),
            ('OfficeRecord', 'user_id'),
            ('OfficeRecord', 'question_id'),
            ('OfficeDocSubmission', 'user_id'),
            ('OfficeDocSubmission', 'exercise_id'),
            ('Problem', 'created_by'),
            ('OfficeQuestion', 'created_by'),
            ('OfficeExercise', 'created_by')
        ) AS targets(table_name, column_name)
    LOOP
        target_table := TO_REGCLASS(FORMAT('%I', target.table_name));
        SELECT attnum INTO target_column
        FROM pg_attribute
        WHERE attrelid = target_table AND attname = target.column_name AND NOT attisdropped;

        FOR existing_fk IN
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = target_table
              AND contype = 'f'
              AND conkey = ARRAY[target_column]::SMALLINT[]
        LOOP
            EXECUTE FORMAT('ALTER TABLE %I DROP CONSTRAINT %I',
                    target.table_name, existing_fk.conname);
        END LOOP;
    END LOOP;
END $$;

ALTER TABLE "Submission"
    ADD CONSTRAINT fk_submission_user
        FOREIGN KEY (user_id) REFERENCES "User"(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_submission_problem
        FOREIGN KEY (problem_id) REFERENCES "Problem"(id) ON DELETE RESTRICT;

ALTER TABLE "OfficeRecord"
    ADD CONSTRAINT fk_office_record_user
        FOREIGN KEY (user_id) REFERENCES "User"(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_office_record_question
        FOREIGN KEY (question_id) REFERENCES "OfficeQuestion"(id) ON DELETE RESTRICT;

ALTER TABLE "OfficeDocSubmission"
    ADD CONSTRAINT fk_office_doc_submission_user
        FOREIGN KEY (user_id) REFERENCES "User"(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_office_doc_submission_exercise
        FOREIGN KEY (exercise_id) REFERENCES "OfficeExercise"(id) ON DELETE RESTRICT;

ALTER TABLE "Problem"
    ADD CONSTRAINT fk_problem_created_by
        FOREIGN KEY (created_by) REFERENCES "User"(id) ON DELETE SET NULL;

ALTER TABLE "OfficeQuestion"
    ADD CONSTRAINT fk_office_question_created_by
        FOREIGN KEY (created_by) REFERENCES "User"(id) ON DELETE SET NULL;

ALTER TABLE "OfficeExercise"
    ADD CONSTRAINT fk_office_exercise_created_by
        FOREIGN KEY (created_by) REFERENCES "User"(id) ON DELETE SET NULL;

ALTER TABLE "User"
    ADD CONSTRAINT ck_user_role CHECK (role IN ('USER', 'TEACHER', 'ADMIN')),
    ADD CONSTRAINT ck_user_solved_count CHECK (solved_count >= 0);

ALTER TABLE "Problem"
    ADD CONSTRAINT ck_problem_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    ADD CONSTRAINT ck_problem_time_limit CHECK (time_limit > 0),
    ADD CONSTRAINT ck_problem_memory_limit CHECK (memory_limit > 0);

ALTER TABLE "Submission"
    ADD CONSTRAINT ck_submission_verdict
        CHECK (verdict IN ('PENDING', 'AC', 'WA', 'TLE', 'RE', 'CE', 'SE')),
    ADD CONSTRAINT ck_submission_time_ms CHECK (time_ms >= 0),
    ADD CONSTRAINT ck_submission_memory_kb CHECK (memory_kb >= 0),
    ADD CONSTRAINT ck_submission_passed CHECK (passed >= 0),
    ADD CONSTRAINT ck_submission_total CHECK (total >= 0),
    ADD CONSTRAINT ck_submission_passed_lte_total CHECK (passed <= total);

ALTER TABLE "OfficeQuestion"
    ADD CONSTRAINT ck_office_question_app_type CHECK (app_type IN ('WORD', 'EXCEL', 'PPT')),
    ADD CONSTRAINT ck_office_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    ADD CONSTRAINT ck_office_question_type
        CHECK (question_type IN ('SINGLE_CHOICE', 'MULTI_CHOICE', 'TRUE_FALSE'));

ALTER TABLE "OfficeExercise"
    ADD CONSTRAINT ck_office_exercise_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));

ALTER TABLE "OfficeDocSubmission"
    ADD CONSTRAINT ck_office_doc_submission_status
        CHECK (status IN ('AUTO_CHECKED', 'NEEDS_REVIEW', 'REVIEWED')),
    ADD CONSTRAINT ck_office_doc_submission_score
        CHECK (score IS NULL OR score BETWEEN 0 AND 100);
