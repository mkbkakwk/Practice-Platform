-- Complete schema for a new Practice Platform database.
-- Quoted CamelCase table names and snake_case columns are retained for
-- compatibility with the existing MyBatis-Plus mappings.

CREATE TABLE "User" (
    id           SERIAL CONSTRAINT pk_user PRIMARY KEY,
    username     VARCHAR(20) CONSTRAINT uk_user_username UNIQUE NOT NULL,
    password     VARCHAR NOT NULL,
    role         VARCHAR NOT NULL DEFAULT 'USER',
    solved_count INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE "Problem" (
    id           SERIAL CONSTRAINT pk_problem PRIMARY KEY,
    slug         VARCHAR(60) CONSTRAINT uk_problem_slug UNIQUE NOT NULL,
    title        VARCHAR(120) NOT NULL,
    description  TEXT NOT NULL,
    input_fmt    TEXT,
    output_fmt   TEXT,
    difficulty   VARCHAR NOT NULL DEFAULT 'EASY',
    time_limit   INT NOT NULL DEFAULT 1000,
    memory_limit INT NOT NULL DEFAULT 256,
    tags         TEXT[] NOT NULL DEFAULT '{}'::TEXT[],
    samples      TEXT NOT NULL DEFAULT '[]',
    test_cases   TEXT NOT NULL DEFAULT '[]',
    visible      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   INT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE "Submission" (
    id         SERIAL CONSTRAINT pk_submission PRIMARY KEY,
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

CREATE TABLE "OfficeQuestion" (
    id            SERIAL CONSTRAINT pk_office_question PRIMARY KEY,
    app_type      VARCHAR(10) NOT NULL,
    category      VARCHAR(60) NOT NULL,
    difficulty    VARCHAR(10) NOT NULL DEFAULT 'EASY',
    question_type VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    options       TEXT NOT NULL DEFAULT '[]',
    answer        VARCHAR(60) NOT NULL,
    explanation   TEXT,
    visible       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    INT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE "OfficeRecord" (
    id          SERIAL CONSTRAINT pk_office_record PRIMARY KEY,
    user_id     INT NOT NULL,
    question_id INT NOT NULL,
    selected    TEXT NOT NULL,
    correct     BOOLEAN NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE "OfficeExercise" (
    id               SERIAL CONSTRAINT pk_office_exercise PRIMARY KEY,
    title            VARCHAR(120) NOT NULL,
    difficulty       VARCHAR(10) NOT NULL DEFAULT 'EASY',
    description      TEXT NOT NULL,
    teacher_doc_path VARCHAR(255),
    teacher_doc_name VARCHAR(255),
    visible          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       INT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE "OfficeDocSubmission" (
    id               SERIAL CONSTRAINT pk_office_doc_submission PRIMARY KEY,
    user_id          INT NOT NULL,
    exercise_id      INT NOT NULL,
    student_doc_path VARCHAR(255) NOT NULL,
    student_doc_name VARCHAR(255) NOT NULL,
    auto_result      TEXT NOT NULL DEFAULT '[]',
    compare_result   TEXT NOT NULL DEFAULT '[]',
    status           VARCHAR(20) NOT NULL DEFAULT 'AUTO_CHECKED',
    score            INT,
    teacher_comment  TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
