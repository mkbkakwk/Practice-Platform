-- Leaderboard: ORDER BY solved_count DESC, created_at ASC.
DROP INDEX IF EXISTS "User_solved_count_idx";
CREATE INDEX "User_solved_count_idx" ON "User" (solved_count DESC, created_at ASC);

-- Public problem lists and teacher-owned management lists.
DROP INDEX IF EXISTS "Problem_created_by_idx";
CREATE INDEX "Problem_created_by_idx" ON "Problem" (created_by, id DESC);
CREATE INDEX "Problem_visible_difficulty_idx" ON "Problem" (visible, difficulty, id);

-- Submission feeds, per-problem cleanup/counts, and solved_count recalculation.
DROP INDEX IF EXISTS "Submission_user_id_idx";
DROP INDEX IF EXISTS "Submission_problem_id_idx";
CREATE INDEX "Submission_user_id_idx" ON "Submission" (user_id, created_at DESC);
CREATE INDEX "Submission_problem_id_idx" ON "Submission" (problem_id, created_at DESC);
CREATE INDEX IF NOT EXISTS "Submission_created_at_idx" ON "Submission" (created_at DESC);
CREATE INDEX "Submission_user_ac_problem_idx"
    ON "Submission" (user_id, problem_id) WHERE verdict = 'AC';

-- Public Office question filters and teacher-owned management lists.
DROP INDEX IF EXISTS "OfficeQuestion_app_type_idx";
DROP INDEX IF EXISTS "OfficeQuestion_created_by_idx";
CREATE INDEX "OfficeQuestion_app_type_idx"
    ON "OfficeQuestion" (app_type, difficulty, visible, id);
CREATE INDEX "OfficeQuestion_created_by_idx" ON "OfficeQuestion" (created_by, id DESC);

-- Per-user statistics and per-question record counts/deletion.
DROP INDEX IF EXISTS "OfficeRecord_user_id_idx";
CREATE INDEX "OfficeRecord_user_id_idx" ON "OfficeRecord" (user_id, question_id);
CREATE INDEX IF NOT EXISTS "OfficeRecord_question_id_idx" ON "OfficeRecord" (question_id);

-- Public exercise lists and teacher-owned management lists.
DROP INDEX IF EXISTS "OfficeExercise_created_by_idx";
CREATE INDEX "OfficeExercise_created_by_idx" ON "OfficeExercise" (created_by, id DESC);
CREATE INDEX "OfficeExercise_visible_idx" ON "OfficeExercise" (visible, id DESC);

-- Student/teacher submission lists and exercise cleanup.
DROP INDEX IF EXISTS "OfficeDocSubmission_user_idx";
DROP INDEX IF EXISTS "OfficeDocSubmission_exercise_idx";
CREATE INDEX "OfficeDocSubmission_user_idx" ON "OfficeDocSubmission" (user_id, id DESC);
CREATE INDEX "OfficeDocSubmission_exercise_idx" ON "OfficeDocSubmission" (exercise_id, id DESC);
