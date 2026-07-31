CREATE TABLE IF NOT EXISTS "User" (
    id           SERIAL PRIMARY KEY,
    username     VARCHAR(20) UNIQUE NOT NULL,
    password     VARCHAR NOT NULL,
    role         VARCHAR NOT NULL DEFAULT 'USER',
    solved_count INT NOT NULL DEFAULT 0,
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
