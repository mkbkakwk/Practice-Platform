package com.oj;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {

    private static final String[] BUSINESS_TABLES = {
            "User", "Problem", "Submission", "OfficeQuestion", "OfficeRecord",
            "OfficeExercise", "OfficeDocSubmission", "judge_outbox"
    };

    @Autowired
    private JdbcTemplate adminJdbc;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    private final Set<String> testSchemas = new LinkedHashSet<>();

    @AfterEach
    void dropTemporarySchemas() {
        for (String schema : testSchemas) {
            adminJdbc.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
        testSchemas.clear();
    }

    @Test
    void freshDatabaseRunsAllMigrationsAndCreatesTheCompleteSchema() {
        TestDatabase database = newDatabase("fresh");
        Flyway flyway = database.flyway();

        flyway.migrate();

        assertThat(database.jdbc().queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ANY (?)
                """, Integer.class, database.schema(), BUSINESS_TABLES)).isEqualTo(8);
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success",
                Integer.class)).isEqualTo(9);
        assertThat(database.jdbc().queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=? AND table_name IN ('Contest', 'ContestParticipant', 'ContestProblem')
                """, Integer.class, database.schema())).isEqualTo(3);
        assertThat(database.jdbc().queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=? AND table_name IN ('Submission', 'OfficeDocSubmission')
                  AND column_name='contest_problem_id'
                """, Integer.class, database.schema())).isEqualTo(2);
        assertThat(database.jdbc().queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema=? AND table_name='User' AND column_name='token_version'
                """, String.class, database.schema())).isEqualTo("0");
        assertThat(database.jdbc().queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=? AND table_name='Submission'
                  AND column_name IN ('judge_token', 'judge_lease_until',
                                      'judge_attempt_count', 'judge_failure_category')
                """, Integer.class, database.schema())).isEqualTo(4);
        assertThat(database.jdbc().queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=? AND table_name='OfficeDocSubmission'
                  AND column_name IN ('judge_version', 'result_detail', 'error_category', 'judged_at')
                """, Integer.class, database.schema())).isEqualTo(4);
        assertThat(database.jdbc().queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema=? AND table_name='OfficeDocSubmission'
                  AND column_name='student_doc_path'
                """, String.class, database.schema())).isEqualTo("YES");
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM \"Problem\"", Integer.class)).isZero();
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM \"OfficeQuestion\"", Integer.class)).isZero();
    }

    @Test
    void repeatedMigrationIsANoOpAndDoesNotInsertDemoData() {
        TestDatabase database = newDatabase("repeat");
        Flyway flyway = database.flyway();
        flyway.migrate();
        Integer historyCount = database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);

        flyway.migrate();

        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success",
                Integer.class)).isEqualTo(historyCount);
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM \"Problem\"", Integer.class)).isZero();
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM \"OfficeQuestion\"", Integer.class)).isZero();
    }

    @Test
    void v8AndV9UpgradeLegacyOfficeContestRowsWithoutChangingTheirDocxTarget() {
        TestDatabase database = newDatabase("office_v8_upgrade");
        Flyway throughV7 = Flyway.configure()
                .dataSource(database.dataSource())
                .defaultSchema(database.schema())
                .schemas(database.schema())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .validateMigrationNaming(true)
                .load();
        throughV7.migrate();
        JdbcTemplate jdbc = database.jdbc();
        int ownerId = insertUser(jdbc, "office_v8_owner");
        int exerciseId = insertExercise(jdbc, ownerId);
        jdbc.update("""
                UPDATE "OfficeExercise"
                SET teacher_doc_path='legacy-reference-id.docx',
                    teacher_doc_name='旧参考文档.docx'
                WHERE id=?
                """, exerciseId);
        int contestId = jdbc.queryForObject("""
                INSERT INTO "Contest" (title, owner_id, start_at, end_at)
                VALUES ('Legacy office contest', ?, NOW() + INTERVAL '1 day', NOW() + INTERVAL '2 days')
                RETURNING id
                """, Integer.class, ownerId);
        long contestProblemId = jdbc.queryForObject("""
                INSERT INTO "ContestProblem"
                    (contest_id, problem_type, office_exercise_id, display_order, label)
                VALUES (?, 'OFFICE', ?, 1, 'A')
                RETURNING id
                """, Long.class, contestId, exerciseId);

        database.flyway().migrate();

        assertThat(jdbc.queryForObject(
                "SELECT problem_type FROM \"ContestProblem\" WHERE id=?",
                String.class, contestProblemId)).isEqualTo("OFFICE_DOCX");
        assertThat(jdbc.queryForObject(
                "SELECT office_exercise_id FROM \"ContestProblem\" WHERE id=?",
                Integer.class, contestProblemId)).isEqualTo(exerciseId);
        assertThat(jdbc.queryForObject(
                "SELECT teacher_doc_path FROM \"OfficeExercise\" WHERE id=?",
                String.class, exerciseId)).isEqualTo("legacy-reference-id.docx");
        assertThat(jdbc.queryForObject(
                "SELECT teacher_doc_name FROM \"OfficeExercise\" WHERE id=?",
                String.class, exerciseId)).isEqualTo("旧参考文档.docx");
        assertThat(jdbc.queryForObject(
                "SELECT starter_doc_path IS NULL AND starter_doc_name IS NULL FROM \"OfficeExercise\" WHERE id=?",
                Boolean.class, exerciseId)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=?
                  AND ((table_name='OfficeExercise' AND column_name IN ('starter_doc_path','starter_doc_name'))
                    OR (table_name='ContestProblem' AND column_name='office_question_id')
                    OR (table_name='OfficeRecord' AND column_name='contest_problem_id'))
                """, Integer.class, database.schema())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT scoring_mode FROM \"Contest\" WHERE id=?", String.class, contestId))
                .isEqualTo("SCORE");
        assertThat(jdbc.queryForObject("SELECT freeze_at IS NULL FROM \"Contest\" WHERE id=?", Boolean.class, contestId))
                .isTrue();
    }

    @Test
    void v9BackfillsHistoricalAlgorithmTerminalResultsFromAV8Schema() {
        TestDatabase database = newDatabase("v8_to_v9_judge_history");
        Flyway throughV8 = Flyway.configure()
                .dataSource(database.dataSource())
                .defaultSchema(database.schema())
                .schemas(database.schema())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .validateMigrationNaming(true)
                .load();
        throughV8.migrate();
        JdbcTemplate jdbc = database.jdbc();
        int ownerId = insertUser(jdbc, "v8_history_owner");
        int problemId = insertProblem(jdbc, "v8-history-problem", ownerId);
        int contestId = jdbc.queryForObject("""
                INSERT INTO "Contest" (title, owner_id, start_at, end_at)
                VALUES ('V8 history contest', ?, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 hour')
                RETURNING id
                """, Integer.class, ownerId);
        long contestProblemId = jdbc.queryForObject("""
                INSERT INTO "ContestProblem"
                    (contest_id, problem_type, algorithm_problem_id, display_order, label)
                VALUES (?, 'ALGORITHM', ?, 1, 'A')
                RETURNING id
                """, Long.class, contestId, problemId);
        int submissionId = jdbc.queryForObject("""
                INSERT INTO "Submission" (user_id, problem_id, contest_problem_id, language, code, verdict,
                    passed, total, time_ms, memory_kb, message)
                VALUES (?, ?, ?, 'python', 'print(1)', 'AC', 1, 1, 7, 512, 'accepted')
                RETURNING id
                """, Integer.class, ownerId, problemId, contestProblemId);

        database.flyway().migrate();

        assertThat(jdbc.queryForObject("SELECT scoring_mode FROM \"Contest\" WHERE id=?", String.class, contestId))
                .isEqualTo("SCORE");
        assertThat(jdbc.queryForObject("SELECT freeze_at IS NULL FROM \"Contest\" WHERE id=?", Boolean.class, contestId))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT judge_generation FROM \"Submission\" WHERE id=?", Integer.class, submissionId))
                .isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM algorithm_judge_history
                WHERE submission_id=? AND judge_generation=0 AND verdict='AC' AND passed=1 AND total=1
                """, Integer.class, submissionId)).isEqualTo(1);
    }

    @Test
    void legacySchemaBaselinesAtV1AndPreservesRepresentativeData() throws Exception {
        TestDatabase database = newDatabase("legacy");
        loadLegacySchema(database);
        JdbcTemplate jdbc = database.jdbc();

        int userId = jdbc.queryForObject("""
                INSERT INTO "User" (username, password, role, solved_count)
                VALUES ('legacy_user', 'hash', 'TEACHER', 1)
                RETURNING id
                """, Integer.class);
        int problemId = jdbc.queryForObject("""
                INSERT INTO "Problem"
                    (slug, title, description, test_cases, created_by)
                VALUES ('legacy-problem', 'Legacy problem', 'kept',
                        '[{"input":"","output":"1"}]', ?)
                RETURNING id
                """, Integer.class, userId);
        jdbc.update("""
                INSERT INTO "Submission"
                    (user_id, problem_id, language, code, verdict, passed, total)
                VALUES (?, ?, 'python', 'print(1)', 'AC', 1, 1)
                """, userId, problemId);
        int questionId = jdbc.queryForObject("""
                INSERT INTO "OfficeQuestion"
                    (app_type, category, difficulty, question_type, content, answer, created_by)
                VALUES ('WORD', 'legacy', 'EASY', 'TRUE_FALSE', 'Legacy question', 'T', ?)
                RETURNING id
                """, Integer.class, userId);
        jdbc.update("""
                INSERT INTO "OfficeRecord" (user_id, question_id, selected, correct)
                VALUES (?, ?, '["T"]', TRUE)
                """, userId, questionId);
        int exerciseId = jdbc.queryForObject("""
                INSERT INTO "OfficeExercise"
                    (title, description, teacher_doc_path, teacher_doc_name, created_by)
                VALUES ('Legacy exercise', 'kept', '/tmp/teacher.docx', 'teacher.docx', ?)
                RETURNING id
                """, Integer.class, userId);
        jdbc.update("""
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name,
                     status, score)
                VALUES (?, ?, '/tmp/student.docx', 'student.docx', 'REVIEWED', 88)
                """, userId, exerciseId);

        database.flyway().migrate();

        assertThat(jdbc.queryForObject("""
                SELECT type FROM flyway_schema_history WHERE version = '1'
                """, String.class)).isEqualTo("BASELINE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success",
                Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT solved_count FROM \"User\" WHERE id=?",
                Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT token_version FROM \"User\" WHERE id=?",
                Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM \"Problem\" WHERE id=?",
                Integer.class, problemId)).isEqualTo(userId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"Submission\" WHERE problem_id=?",
                Integer.class, problemId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"OfficeRecord\" WHERE question_id=?",
                Integer.class, questionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM \"OfficeExercise\" WHERE id=?",
                Integer.class, exerciseId)).isEqualTo(userId);
        assertThat(jdbc.queryForObject(
                "SELECT score FROM \"OfficeDocSubmission\" WHERE exercise_id=?",
                Integer.class, exerciseId)).isEqualTo(88);
        assertThat(jdbc.queryForObject(
                "SELECT judge_version FROM \"OfficeDocSubmission\" WHERE exercise_id=?",
                String.class, exerciseId)).isEqualTo("legacy");
        assertThat(jdbc.queryForObject(
                "SELECT result_detail::text FROM \"OfficeDocSubmission\" WHERE exercise_id=?",
                String.class, exerciseId)).isEqualTo("{}");
    }

    @Test
    void orphanSubmissionStopsMigrationWithoutDeletingTheRow() throws Exception {
        TestDatabase database = newDatabase("orphan");
        loadLegacySchema(database);
        database.jdbc().update("""
                INSERT INTO "Submission" (user_id, problem_id, language, code)
                VALUES (999999, 999999, 'python', 'print(1)')
                """);

        assertThatThrownBy(() -> database.flyway().migrate())
                .hasStackTraceContaining(
                        "Orphan check failed: Submission.user_id has 1 orphan row");

        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM \"Submission\"", Integer.class)).isEqualTo(1);
        assertThat(database.jdbc().queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE connamespace = ?::regnamespace AND conname = 'fk_submission_user'
                """, Integer.class, database.schema())).isZero();
    }

    @Test
    void foreignKeysChecksUniqueUsernameAndNullableCreatorsAreEnforced() {
        TestDatabase database = newDatabase("constraints");
        database.flyway().migrate();
        JdbcTemplate jdbc = database.jdbc();

        int userId = insertUser(jdbc, "valid_user");
        int problemId = insertProblem(jdbc, "valid-problem", userId);
        int questionId = insertQuestion(jdbc, userId);
        int exerciseId = insertExercise(jdbc, userId);

        jdbc.update("""
                INSERT INTO "Submission"
                    (user_id, problem_id, language, code, verdict, passed, total)
                VALUES (?, ?, 'python', 'print(1)', 'AC', 1, 1)
                """, userId, problemId);
        jdbc.update("""
                INSERT INTO "OfficeRecord" (user_id, question_id, selected, correct)
                VALUES (?, ?, '["T"]', TRUE)
                """, userId, questionId);
        int documentSubmissionId = jdbc.queryForObject("""
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name, status)
                VALUES (?, ?, '/tmp/valid.docx', 'valid.docx', 'AUTO_CHECKED')
                RETURNING id
                """, Integer.class, userId, exerciseId);
        insertProblem(jdbc, "system-problem", null);

        assertDatabaseRejects(jdbc, """
                INSERT INTO "Submission" (user_id, problem_id, language, code)
                VALUES (999999, ?, 'python', 'print(1)')
                """, problemId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "Submission" (user_id, problem_id, language, code)
                VALUES (?, 999999, 'python', 'print(1)')
                """, userId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "OfficeRecord" (user_id, question_id, selected, correct)
                VALUES (999999, ?, '["T"]', TRUE)
                """, questionId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "OfficeRecord" (user_id, question_id, selected, correct)
                VALUES (?, 999999, '["T"]', TRUE)
                """, userId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name)
                VALUES (999999, ?, '/tmp/invalid.docx', 'invalid.docx')
                """, exerciseId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name)
                VALUES (?, 999999, '/tmp/invalid.docx', 'invalid.docx')
                """, userId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "Problem" (slug, title, description, test_cases, created_by)
                VALUES ('bad-creator-problem', 'bad', 'bad', '[{"input":"","output":""}]', 999999)
                """);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "OfficeQuestion"
                    (app_type, category, question_type, content, answer, created_by)
                VALUES ('WORD', 'bad', 'TRUE_FALSE', 'bad creator', 'T', 999999)
                """);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "OfficeExercise" (title, description, created_by)
                VALUES ('bad creator', 'bad', 999999)
                """);

        assertDatabaseRejects(jdbc,
                "UPDATE \"User\" SET role='SUPERUSER' WHERE id=?", userId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"User\" SET solved_count=-1 WHERE id=?", userId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"User\" SET token_version=-1 WHERE id=?", userId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"Problem\" SET time_limit=0 WHERE id=?", problemId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"Submission\" SET verdict='UNKNOWN' WHERE problem_id=?", problemId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"Submission\" SET passed=2, total=1 WHERE problem_id=?", problemId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"OfficeDocSubmission\" SET score=101 WHERE id=?", documentSubmissionId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"OfficeDocSubmission\" SET judge_version=' ' WHERE id=?", documentSubmissionId);
        assertDatabaseRejects(jdbc,
                "UPDATE \"OfficeDocSubmission\" SET result_detail=to_jsonb(repeat('x', 262145)) WHERE id=?",
                documentSubmissionId);
        assertDatabaseRejects(jdbc, """
                INSERT INTO "User" (username, password, role)
                VALUES ('valid_user', 'hash', 'USER')
                """);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"Problem\" WHERE created_by IS NULL",
                Integer.class)).isEqualTo(1);
    }

    private TestDatabase newDatabase(String label) {
        String schema = "flyway_" + label + "_"
                + UUID.randomUUID().toString().replace("-", "");
        adminJdbc.execute("CREATE SCHEMA \"" + schema + "\"");
        testSchemas.add(schema);

        String separator = databaseUrl.contains("?") ? "&" : "?";
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(databaseUrl + separator + "currentSchema=" + schema);
        dataSource.setUsername(databaseUsername);
        dataSource.setPassword(databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .validateMigrationNaming(true)
                .load();
        return new TestDatabase(schema, dataSource, jdbc, flyway);
    }

    private void loadLegacySchema(TestDatabase database) throws Exception {
        try (Connection connection = database.dataSource().getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection, new ClassPathResource("legacy-schema.sql"));
        }
    }

    private int insertUser(JdbcTemplate jdbc, String username) {
        return jdbc.queryForObject("""
                INSERT INTO "User" (username, password, role)
                VALUES (?, 'hash', 'USER')
                RETURNING id
                """, Integer.class, username);
    }

    private int insertProblem(JdbcTemplate jdbc, String slug, Integer createdBy) {
        return jdbc.queryForObject("""
                INSERT INTO "Problem"
                    (slug, title, description, test_cases, created_by)
                VALUES (?, 'Problem', 'description',
                        '[{"input":"","output":"1"}]', ?)
                RETURNING id
                """, Integer.class, slug, createdBy);
    }

    private int insertQuestion(JdbcTemplate jdbc, int createdBy) {
        return jdbc.queryForObject("""
                INSERT INTO "OfficeQuestion"
                    (app_type, category, difficulty, question_type, content, answer, created_by)
                VALUES ('WORD', 'test', 'EASY', 'TRUE_FALSE', 'Question', 'T', ?)
                RETURNING id
                """, Integer.class, createdBy);
    }

    private int insertExercise(JdbcTemplate jdbc, int createdBy) {
        return jdbc.queryForObject("""
                INSERT INTO "OfficeExercise" (title, description, created_by)
                VALUES ('Exercise', 'description', ?)
                RETURNING id
                """, Integer.class, createdBy);
    }

    private void assertDatabaseRejects(JdbcTemplate jdbc, String sql, Object... arguments) {
        assertThatThrownBy(() -> jdbc.update(sql, arguments))
                .isInstanceOf(DataAccessException.class);
    }

    private record TestDatabase(
            String schema,
            DataSource dataSource,
            JdbcTemplate jdbc,
            Flyway flyway) {
    }
}
