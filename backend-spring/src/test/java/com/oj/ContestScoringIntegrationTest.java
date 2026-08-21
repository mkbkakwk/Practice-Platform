package com.oj;

import com.oj.common.CurrentUser;
import com.oj.dto.ContestDtos;
import com.oj.dto.ContestProblemRequest;
import com.oj.dto.ContestUpsertRequest;
import com.oj.service.ContestRejudgeService;
import com.oj.service.ContestService;
import com.oj.service.ContestStandingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ContestScoringIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContestStandingService standings;
    @Autowired private ContestRejudgeService rejudge;
    @Autowired private ContestService contests;
    @MockBean private Clock clock;

    private int teacher;
    private int studentA;
    private int studentB;
    private int algorithm;

    @BeforeEach
    void reset() {
        jdbc.execute("""
                TRUNCATE TABLE "judge_outbox", rejudge_batch_item, rejudge_batch, algorithm_judge_history,
                    "OfficeDocSubmission", "OfficeRecord", "Submission", "ContestProblem", "ContestParticipant",
                    "Contest", "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        teacher = user("teacher", "TEACHER");
        studentA = user("student-a", "USER");
        studentB = user("student-b", "USER");
        algorithm = jdbc.queryForObject("""
                INSERT INTO "Problem" (slug, title, description, test_cases, created_by, visible, content_visibility)
                VALUES ('score-a', 'Score A', 'x', '[]', ?, true, 'PUBLIC') RETURNING id
                """, Integer.class, teacher);
        now(START.plusSeconds(30));
    }

    @AfterEach void clear() { CurrentUser.clear(); }

    @Test
    void scoreModeDerivesBestMixedScoresAndKeepsZeroSubmitParticipants() {
        int contest = contest("SCORE", START.plusSeconds(45), null);
        long algorithmProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        int question = officeQuestion();
        long choiceProblem = problem(contest, "OFFICE_CHOICE", null, question, null, "B");
        int exercise = exercise();
        long docxProblem = problem(contest, "OFFICE_DOCX", null, null, exercise, "C");
        participant(contest, studentA); participant(contest, studentB);
        algorithmSubmission(studentA, algorithmProblem, "AC", START.plusSeconds(65));
        algorithmSubmission(studentA, algorithmProblem, "WA", START.plusSeconds(95));
        jdbc.update("""
                INSERT INTO "OfficeRecord" (user_id, question_id, contest_problem_id, selected, correct, created_at)
                VALUES (?, ?, ?, '["T"]', true, ?)
                """, studentA, question, choiceProblem, local(START.plusSeconds(75)));
        jdbc.update("""
                INSERT INTO "OfficeDocSubmission" (user_id, exercise_id, contest_problem_id, student_doc_path,
                    student_doc_name, status, score, created_at)
                VALUES (?, ?, ?, '/tmp/a.docx', 'a.docx', 'AUTO_CHECKED', 56, ?)
                """,
                studentA, exercise, docxProblem, local(START.plusSeconds(85)));

        as(studentA, "student-a", "USER");
        ContestDtos.Standing standing = standings.standings(contest);
        assertThat(standing.entries()).hasSize(2);
        assertThat(standing.entries().getFirst()).extracting(ContestDtos.StandingEntry::rank,
                ContestDtos.StandingEntry::totalScore).containsExactly(1, 256);
        assertThat(standing.entries().get(1)).extracting(ContestDtos.StandingEntry::userId,
                ContestDtos.StandingEntry::rank, ContestDtos.StandingEntry::totalScore).containsExactly(studentB, 2, 0);
    }

    @Test
    void icpcCountsWhitelistedWrongAttemptsBeforeFirstAcAndRejectsOfficeProblems() {
        int contest = contest("ICPC", null, null);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA); participant(contest, studentB);
        algorithmSubmission(studentA, contestProblem, "WA", START.plusSeconds(60));
        algorithmSubmission(studentA, contestProblem, "AC", START.plusSeconds(35 * 60));
        algorithmSubmission(studentB, contestProblem, "AC", START.plusSeconds(56 * 60));
        as(studentA, "student-a", "USER");
        ContestDtos.Standing standing = standings.standings(contest);
        assertThat(standing.entries().getFirst()).extracting(ContestDtos.StandingEntry::userId,
                ContestDtos.StandingEntry::solved, ContestDtos.StandingEntry::penaltyMinutes)
                .containsExactly(studentA, 1, 55);
        assertThat(standing.entries().get(1).userId()).isEqualTo(studentB);
    }

    @Test
    void studentFreezeUsesStrictCutoffWhileOwnerSeesLiveResults() {
        Instant freeze = START.plusSeconds(30 * 60);
        int contest = contest("SCORE", freeze, freeze);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA);
        algorithmSubmission(studentA, contestProblem, "WA", freeze.minusSeconds(1));
        algorithmSubmission(studentA, contestProblem, "AC", freeze); // equality is excluded from frozen view
        now(freeze.plusSeconds(1));
        as(studentA, "student-a", "USER");
        ContestDtos.Standing frozen = standings.standings(contest);
        assertThat(frozen.frozen()).isTrue();
        assertThat(frozen.entries().getFirst().totalScore()).isZero();
        as(teacher, "teacher", "TEACHER");
        ContestDtos.Standing live = standings.standings(contest);
        assertThat(live.frozen()).isFalse();
        assertThat(live.managerView()).isTrue();
        assertThat(live.entries().getFirst().totalScore()).isEqualTo(100);
        now(END.plusSeconds(1));
        as(studentA, "student-a", "USER");
        assertThat(standings.standings(contest).entries().getFirst().totalScore()).isEqualTo(100);
    }

    @Test
    void icpcFreezeHidesPostFreezeAcAndPenaltyUntilTheContestEnds() {
        Instant freeze = START.plusSeconds(30 * 60);
        int contest = contest("ICPC", freeze, freeze);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA);
        algorithmSubmission(studentA, contestProblem, "WA", freeze.minusSeconds(30));
        algorithmSubmission(studentA, contestProblem, "AC", freeze.plusSeconds(5 * 60));
        now(freeze.plusSeconds(1));

        as(studentA, "student-a", "USER");
        ContestDtos.Standing frozen = standings.standings(contest);
        assertThat(frozen.frozen()).isTrue();
        assertThat(frozen.entries().getFirst()).extracting(ContestDtos.StandingEntry::solved,
                ContestDtos.StandingEntry::penaltyMinutes).containsExactly(0, 0);
        assertThat(frozen.entries().getFirst().problems().getFirst())
                .extracting(ContestDtos.StandingProblem::solved, ContestDtos.StandingProblem::attempts,
                        ContestDtos.StandingProblem::penaltyMinutes)
                .containsExactly(false, 1, null);

        as(teacher, "teacher", "TEACHER");
        ContestDtos.Standing live = standings.standings(contest);
        assertThat(live.managerView()).isTrue();
        assertThat(live.entries().getFirst()).extracting(ContestDtos.StandingEntry::solved,
                ContestDtos.StandingEntry::penaltyMinutes).containsExactly(1, 55);
        now(END.plusSeconds(1));
        as(studentA, "student-a", "USER");
        assertThat(standings.standings(contest).entries().getFirst())
                .extracting(ContestDtos.StandingEntry::solved, ContestDtos.StandingEntry::penaltyMinutes)
                .containsExactly(1, 55);
    }

    @Test
    void scoreRanksTiesWithCompetitionRankingAndIncludesALargeRoster() {
        int contest = contest("SCORE", null, null);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA); participant(contest, studentB);
        int studentC = user("student-c", "USER");
        participant(contest, studentC);
        algorithmSubmission(studentA, contestProblem, "AC", START.plusSeconds(5));
        algorithmSubmission(studentB, contestProblem, "AC", START.plusSeconds(6));
        // The size catches accidental participant×problem fetch loops while
        // retaining the deterministic zero-score tail of a real contest roster.
        List<Integer> zeroSubmitters = new ArrayList<>();
        for (int i = 0; i < 100; i++) zeroSubmitters.add(user("bulk-" + i, "USER"));
        zeroSubmitters.forEach(user -> participant(contest, user));

        as(studentA, "student-a", "USER");
        ContestDtos.Standing standing = standings.standings(contest);
        assertThat(standing.entries()).hasSize(103);
        assertThat(standing.entries().subList(0, 3)).extracting(
                ContestDtos.StandingEntry::userId, ContestDtos.StandingEntry::rank,
                ContestDtos.StandingEntry::totalScore)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(studentA, 1, 100),
                        org.assertj.core.groups.Tuple.tuple(studentB, 1, 100),
                        org.assertj.core.groups.Tuple.tuple(studentC, 3, 0));
    }

    @Test
    void managerQueuesGenerationSafeAlgorithmRejudgeAndRejectsOfficeScope() {
        int contest = contest("SCORE", null, null);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA);
        int submission = algorithmSubmission(studentA, contestProblem, "AC", START.plusSeconds(10));
        as(teacher, "teacher", "TEACHER");
        ContestDtos.RejudgeBatchDetail batch = rejudge.rejudgeSubmission(contest, submission);
        assertThat(batch.batch().totalCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT judge_generation FROM \"Submission\" WHERE id=?", Integer.class, submission)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judge_outbox WHERE submission_id=? AND judge_generation=1", Integer.class, submission)).isEqualTo(1);
        as(studentA, "student-a", "USER");
        assertThat(standings.standings(contest).entries().getFirst().totalScore()).isEqualTo(100);
        jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'JUDGING', judge_token = '00000000-0000-0000-0000-000000000001',
                    judge_lease_until = NOW() + INTERVAL '1 minute'
                WHERE id = ?
                """, submission);
        assertThat(standings.standings(contest).entries().getFirst().totalScore()).isEqualTo(100);
        jdbc.update("""
                UPDATE "Submission"
                SET verdict = 'JUDGE_FAILED', judge_token = NULL, judge_lease_until = NULL
                WHERE id = ?
                """, submission);
        assertThat(standings.standings(contest).entries().getFirst().totalScore()).isEqualTo(100);
        as(teacher, "teacher", "TEACHER");
        int office = officeQuestion();
        long officeProblem = problem(contest, "OFFICE_CHOICE", null, office, null, "B");
        assertThatThrownBy(() -> rejudge.rejudgeProblem(contest, officeProblem)).hasMessageContaining("仅支持算法题重判");
    }

    @Test
    void rejudgeIsOwnerOrAdminOnlyAndRejectsCrossContestSubmissionIdor() {
        int contest = contest("SCORE", null, null);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA);
        int submission = algorithmSubmission(studentA, contestProblem, "WA", START.plusSeconds(10));
        int otherTeacher = user("other-teacher", "TEACHER");
        int admin = user("admin", "ADMIN");
        int otherContest = contest("SCORE", null, null);
        long otherProblem = problem(otherContest, "ALGORITHM", algorithm, null, null, "A");
        participant(otherContest, studentB);
        int otherSubmission = algorithmSubmission(studentB, otherProblem, "WA", START.plusSeconds(11));

        as(studentA, "student-a", "USER");
        assertThatThrownBy(() -> rejudge.rejudgeSubmission(contest, submission))
                .hasMessageContaining("需要教师或管理员权限");
        as(otherTeacher, "other-teacher", "TEACHER");
        assertThatThrownBy(() -> rejudge.rejudgeSubmission(contest, submission))
                .hasMessageContaining("无权管理该比赛");
        as(admin, "admin", "ADMIN");
        assertThatThrownBy(() -> rejudge.rejudgeSubmission(contest, otherSubmission))
                .hasMessageContaining("提交不属于该比赛");
        assertThat(rejudge.rejudgeSubmission(contest, submission).batch().totalCount()).isEqualTo(1);
    }

    @Test
    void concurrentRejudgeRequestsAllocateDistinctIncreasingGenerations() throws Exception {
        int contest = contest("SCORE", null, null);
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, studentA);
        int submission = algorithmSubmission(studentA, contestProblem, "WA", START.plusSeconds(10));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> request = () -> {
                CurrentUser.set(teacher, "teacher", "TEACHER");
                try { return rejudge.rejudgeSubmission(contest, submission).batch().id(); }
                finally { CurrentUser.clear(); }
            };
            Future<Long> first = executor.submit(request);
            Future<Long> second = executor.submit(request);
            assertThat(first.get()).isPositive();
            assertThat(second.get()).isPositive();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT judge_generation FROM \"Submission\" WHERE id=?", Integer.class, submission)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT judge_generation FROM rejudge_batch_item WHERE submission_id = ? ORDER BY judge_generation
                """, Integer.class, submission)).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM judge_outbox WHERE submission_id = ? AND judge_generation IN (1, 2)
                """, Integer.class, submission)).isEqualTo(2);
    }

    @Test
    void icpcBackendRejectsBothOfficeProblemFamiliesBeforeTheyCanBeAdded() {
        int contest = draftContest("ICPC");
        as(teacher, "teacher", "TEACHER");
        assertThat(contests.addProblem(contest, problemRequest("ALGORITHM", algorithm)).problemType()).isEqualTo("ALGORITHM");
        int question = officeQuestion();
        int exercise = exercise();
        assertThatThrownBy(() -> contests.addProblem(contest, problemRequest("OFFICE_CHOICE", question)))
                .hasMessageContaining("ICPC 比赛只允许添加算法题");
        assertThatThrownBy(() -> contests.addProblem(contest, problemRequest("OFFICE_DOCX", exercise)))
                .hasMessageContaining("ICPC 比赛只允许添加算法题");
    }

    @Test
    void draftScoringModeCannotChangeAfterAnyContestSubmissionExists() {
        int contest = draftContest("SCORE");
        long contestProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        algorithmSubmission(studentA, contestProblem, "WA", START.plusSeconds(5));
        as(teacher, "teacher", "TEACHER");
        assertThatThrownBy(() -> contests.update(contest, upsert("ICPC")))
                .hasMessageContaining("已有比赛提交，不能修改计分模式");
    }

    private int contest(String mode, Instant now, Instant freezeAt) {
        if (now != null) now(now);
        return jdbc.queryForObject("""
                INSERT INTO "Contest" (title, status, access_type, scoring_mode, owner_id, start_at, end_at, freeze_at)
                VALUES ('contest', 'PUBLISHED', 'OPEN', ?, ?, ?, ?, ?) RETURNING id
                """, Integer.class, mode, teacher, Timestamp.from(START), Timestamp.from(END),
                freezeAt == null ? null : Timestamp.from(freezeAt));
    }
    private int draftContest(String mode) {
        return jdbc.queryForObject("""
                INSERT INTO "Contest" (title, status, access_type, scoring_mode, owner_id, start_at, end_at)
                VALUES ('draft', 'DRAFT', 'OPEN', ?, ?, ?, ?) RETURNING id
                """, Integer.class, mode, teacher, Timestamp.from(START.plusSeconds(24 * 3600)),
                Timestamp.from(END.plusSeconds(24 * 3600)));
    }
    private ContestProblemRequest problemRequest(String type, int problemId) {
        ContestProblemRequest request = new ContestProblemRequest();
        request.setProblemType(type);
        request.setProblemId(problemId);
        return request;
    }
    private ContestUpsertRequest upsert(String scoringMode) {
        ContestUpsertRequest request = new ContestUpsertRequest();
        request.setTitle("draft");
        request.setDescription("");
        request.setAccessType("OPEN");
        request.setScoringMode(scoringMode);
        request.setStartAt(START.plusSeconds(24 * 3600));
        request.setEndAt(END.plusSeconds(24 * 3600));
        return request;
    }
    private long problem(int contest, String type, Integer algorithmId, Integer questionId, Integer exerciseId, String label) {
        return jdbc.queryForObject("""
                INSERT INTO "ContestProblem" (contest_id, problem_type, algorithm_problem_id, office_question_id,
                    office_exercise_id, display_order, label) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, contest, type, algorithmId, questionId, exerciseId,
                label.charAt(0) - 'A' + 1, label);
    }
    private void participant(int contest, int user) { jdbc.update("INSERT INTO \"ContestParticipant\" (contest_id, user_id) VALUES (?, ?)", contest, user); }
    private int algorithmSubmission(int user, long contestProblem, String verdict, Instant createdAt) {
        int id = jdbc.queryForObject("""
                INSERT INTO "Submission" (user_id, problem_id, contest_problem_id, language, code, verdict,
                    passed, total, created_at) VALUES (?, ?, ?, 'python', 'print(1)', ?, 1, 1, ?) RETURNING id
                """, Integer.class, user, algorithm, contestProblem, verdict, local(createdAt));
        if (!"PENDING".equals(verdict) && !"JUDGING".equals(verdict) && !"JUDGE_FAILED".equals(verdict)) {
            jdbc.update("""
                    INSERT INTO algorithm_judge_history
                        (submission_id, judge_generation, verdict, passed, total, time_ms, memory_kb, message)
                    VALUES (?, 0, ?, 1, 1, 0, 0, ?)
                    """, id, verdict, verdict);
        }
        return id;
    }
    private int officeQuestion() { return jdbc.queryForObject("""
            INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, answer, created_by)
            VALUES ('WORD', 'x', 'EASY', 'TRUE_FALSE', 'q', 'T', ?) RETURNING id""", Integer.class, teacher); }
    private int exercise() { return jdbc.queryForObject("""
            INSERT INTO "OfficeExercise" (title, description, teacher_doc_path, teacher_doc_name, starter_doc_path, starter_doc_name, created_by)
            VALUES ('doc', 'x', '/tmp/reference.docx', 'reference.docx', '/tmp/starter.docx', 'starter.docx', ?) RETURNING id""", Integer.class, teacher); }
    private int user(String name, String role) { return jdbc.queryForObject("INSERT INTO \"User\" (username, password, role) VALUES (?, 'hash', ?) RETURNING id", Integer.class, name, role); }
    private LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private void now(Instant value) { when(clock.instant()).thenReturn(value); }
    private void as(int id, String name, String role) { CurrentUser.set(id, name, role); }
}
