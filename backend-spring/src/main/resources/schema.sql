-- Schema for Practice Platform. Idempotent — safe to run on every startup.
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
    samples      JSONB NOT NULL DEFAULT '[]',
    test_cases   JSONB NOT NULL DEFAULT '[]',
    visible      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

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
