-- Legacy pre-Flyway schema fixture.
-- Used only by migration upgrade tests; it is not packaged as a production
-- initialization script and must not be edited as the authoritative schema.
-- Flyway migrations under src/main/resources/db/migration are authoritative.
-- Table names keep the quoted CamelCase form so the entity @TableName values
-- stay unchanged. Column names use snake_case so MyBatis-Plus'
-- map-underscore-to-camel-case maps them to Java camelCase fields.

CREATE TABLE IF NOT EXISTS "User" (
    id           SERIAL PRIMARY KEY,
    username     VARCHAR(20) UNIQUE NOT NULL,
    password     VARCHAR NOT NULL,
    role         VARCHAR NOT NULL DEFAULT 'USER',
    solved_count INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS "User_solved_count_idx" ON "User" (solved_count);

CREATE TABLE IF NOT EXISTS "Problem" (
    id           SERIAL PRIMARY KEY,
    slug         VARCHAR(60) UNIQUE NOT NULL,
    title        VARCHAR(120) NOT NULL,
    description  TEXT NOT NULL,
    input_fmt    TEXT,
    output_fmt   TEXT,
    difficulty   VARCHAR NOT NULL DEFAULT 'EASY',
    time_limit   INT NOT NULL DEFAULT 1000,
    memory_limit INT NOT NULL DEFAULT 256,
    tags         TEXT[] NOT NULL DEFAULT '{}',
    samples      TEXT NOT NULL DEFAULT '[]',
    test_cases   TEXT NOT NULL DEFAULT '[]',
    visible      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   INT REFERENCES "User"(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE "Problem" ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE "Problem" ADD COLUMN IF NOT EXISTS created_by INT REFERENCES "User"(id) ON DELETE SET NULL;
UPDATE "Problem"
SET created_by = NULLIF(to_jsonb("Problem") ->> 'creator_id', '')::INT
WHERE created_by IS NULL AND to_jsonb("Problem") ? 'creator_id';
ALTER TABLE "Problem" DROP COLUMN IF EXISTS creator_id;
CREATE INDEX IF NOT EXISTS "Problem_created_by_idx" ON "Problem" (created_by);

CREATE TABLE IF NOT EXISTS "Submission" (
    id         SERIAL PRIMARY KEY,
    user_id    INT NOT NULL,
    problem_id INT NOT NULL,
    language   VARCHAR(20) NOT NULL,
    code       TEXT NOT NULL,
    verdict    VARCHAR NOT NULL DEFAULT 'PENDING',
    time_ms    INT NOT NULL DEFAULT 0,
    memory_kb  INT NOT NULL DEFAULT 0,
    message    TEXT,
    passed     INT NOT NULL DEFAULT 0,
    total      INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS "Submission_user_id_idx"     ON "Submission" (user_id);
CREATE INDEX IF NOT EXISTS "Submission_problem_id_idx"  ON "Submission" (problem_id);
CREATE INDEX IF NOT EXISTS "Submission_created_at_idx"  ON "Submission" (created_at DESC);

-- Office operation practice module.
-- options: JSON text array of option strings, e.g. '["A. ...","B. ..."]'
-- answer: SINGLE_CHOICE = "0" (option index); MULTI_CHOICE = "0,2" (sorted indices); TRUE_FALSE = "T"/"F"
CREATE TABLE IF NOT EXISTS "OfficeQuestion" (
    id            SERIAL PRIMARY KEY,
    app_type      VARCHAR(10) NOT NULL,         -- WORD / EXCEL / PPT
    category      VARCHAR(60) NOT NULL,          -- e.g. 文字排版 / 公式函数 / 动画
    difficulty    VARCHAR(10) NOT NULL DEFAULT 'EASY',
    question_type VARCHAR(20) NOT NULL,          -- SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE
    content       TEXT NOT NULL,
    options       TEXT NOT NULL DEFAULT '[]',    -- JSON text array
    answer        VARCHAR(60) NOT NULL,
    explanation   TEXT,
    visible       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    INT REFERENCES "User"(id) ON DELETE SET NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE "OfficeQuestion" ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE "OfficeQuestion" ADD COLUMN IF NOT EXISTS created_by INT REFERENCES "User"(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS "OfficeQuestion_app_type_idx" ON "OfficeQuestion" (app_type);
CREATE INDEX IF NOT EXISTS "OfficeQuestion_created_by_idx" ON "OfficeQuestion" (created_by);

CREATE TABLE IF NOT EXISTS "OfficeRecord" (
    id          SERIAL PRIMARY KEY,
    user_id     INT NOT NULL,
    question_id INT NOT NULL,
    selected    TEXT NOT NULL,                   -- JSON text: ["0"] or ["0","2"] or ["T"]
    correct     BOOLEAN NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS "OfficeRecord_user_id_idx"     ON "OfficeRecord" (user_id);
CREATE INDEX IF NOT EXISTS "OfficeRecord_question_id_idx" ON "OfficeRecord" (question_id);

-- Document typesetting exercises (文档排版练习). Student uploads a .docx; the
-- backend parses it with Apache POI, extracts per-paragraph formatting, and
-- compares against the teacher's reference document. Discrepancies are flagged
-- for manual teacher review.
CREATE TABLE IF NOT EXISTS "OfficeExercise" (
    id               SERIAL PRIMARY KEY,
    title            VARCHAR(120) NOT NULL,
    difficulty       VARCHAR(10) NOT NULL DEFAULT 'EASY',
    description      TEXT NOT NULL,              -- Markdown: formatting requirements for students
    teacher_doc_path VARCHAR(255),               -- server path to the teacher's reference .docx
    teacher_doc_name VARCHAR(255),               -- original filename of the teacher's doc
    visible          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       INT REFERENCES "User"(id) ON DELETE SET NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE "OfficeExercise" ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE "OfficeExercise" ADD COLUMN IF NOT EXISTS created_by INT REFERENCES "User"(id) ON DELETE SET NULL;
UPDATE "OfficeExercise"
SET created_by = NULLIF(to_jsonb("OfficeExercise") ->> 'creator_id', '')::INT
WHERE created_by IS NULL AND to_jsonb("OfficeExercise") ? 'creator_id';
ALTER TABLE "OfficeExercise" DROP COLUMN IF EXISTS creator_id;
CREATE INDEX IF NOT EXISTS "OfficeExercise_created_by_idx" ON "OfficeExercise" (created_by);

CREATE TABLE IF NOT EXISTS "OfficeDocSubmission" (
    id               SERIAL PRIMARY KEY,
    user_id          INT NOT NULL,
    exercise_id      INT NOT NULL,
    student_doc_path VARCHAR(255) NOT NULL,      -- server path to the student's uploaded .docx
    student_doc_name VARCHAR(255) NOT NULL,      -- original filename
    auto_result      TEXT NOT NULL DEFAULT '[]', -- JSON: per-paragraph formatting extracted from student doc
    compare_result   TEXT NOT NULL DEFAULT '[]', -- JSON: per-paragraph diff vs teacher doc
    status           VARCHAR(20) NOT NULL DEFAULT 'AUTO_CHECKED', -- AUTO_CHECKED / NEEDS_REVIEW / REVIEWED
    score            INT,                        -- final score (set by teacher, null until reviewed)
    teacher_comment  TEXT,                       -- teacher's review comment
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS "OfficeDocSubmission_user_idx"     ON "OfficeDocSubmission" (user_id);
CREATE INDEX IF NOT EXISTS "OfficeDocSubmission_exercise_idx" ON "OfficeDocSubmission" (exercise_id);
