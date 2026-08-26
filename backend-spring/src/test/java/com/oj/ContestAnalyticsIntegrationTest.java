package com.oj;

import com.oj.common.CurrentUser;
import com.oj.dto.ContestDtos;
import com.oj.service.ContestAnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContestAnalyticsIntegrationTest {
    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContestAnalyticsService analytics;
    @Autowired private MockMvc mockMvc;
    @MockBean private Clock clock;

    private int owner;
    private int student;
    private int inactive;
    private int algorithm;

    @BeforeEach
    void reset() {
        jdbc.execute("""
                TRUNCATE TABLE "judge_outbox", rejudge_batch_item, rejudge_batch, algorithm_judge_history,
                    "OfficeDocSubmission", "OfficeRecord", "Submission", "ContestProblem", "ContestParticipant",
                    "Contest", "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        owner = user("owner", "TEACHER");
        student = user("student", "USER");
        inactive = user("inactive", "USER");
        algorithm = jdbc.queryForObject("""
                INSERT INTO "Problem" (slug, title, description, test_cases, created_by, visible, content_visibility)
                VALUES ('analytics-a', 'Analytics A', 'x', '[]', ?, true, 'PUBLIC') RETURNING id
                """, Integer.class, owner);
        now(START.plusSeconds(30));
    }

    @AfterEach void clearUser() { CurrentUser.clear(); }

    @Test
    void scoreAnalyticsUsesRawSubmissionsButEffectiveAlgorithmResults() {
        int contest = contest("SCORE", START.plusSeconds(45), null);
        long algorithmProblem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        int question = question();
        long choiceProblem = problem(contest, "OFFICE_CHOICE", null, question, null, "B");
        int exercise = exercise();
        long docxProblem = problem(contest, "OFFICE_DOCX", null, null, exercise, "C");
        participant(contest, student);
        participant(contest, inactive);

        int submission = algorithmSubmission(student, algorithmProblem, "JUDGE_FAILED", START.plusSeconds(60));
        jdbc.update("""
                INSERT INTO algorithm_judge_history
                (submission_id, judge_generation, verdict, passed, total, time_ms, memory_kb, message)
                VALUES (?, 0, 'AC', 1, 1, 1, 1, 'accepted')
                """, submission);
        jdbc.update("""
                INSERT INTO "OfficeRecord" (user_id, question_id, contest_problem_id, selected, correct, created_at)
                VALUES (?, ?, ?, '["T"]', true, ?)
                """, student, question, choiceProblem, local(START.plusSeconds(70)));
        jdbc.update("""
                INSERT INTO "OfficeDocSubmission" (user_id, exercise_id, contest_problem_id, student_doc_path,
                student_doc_name, status, score, created_at) VALUES (?, ?, ?, '/tmp/a.docx', 'a.docx', 'NEEDS_REVIEW', 56, ?)
                """,
                student, exercise, docxProblem, local(START.plusSeconds(80)));

        now(END.plusSeconds(1));
        as(owner, "owner", "TEACHER");
        ContestDtos.Analytics result = analytics.analytics(contest);
        assertThat(result.overview()).extracting(ContestDtos.Overview::participantCount,
                ContestDtos.Overview::activeParticipantCount, ContestDtos.Overview::inactiveParticipantCount,
                ContestDtos.Overview::totalSubmissionCount, ContestDtos.Overview::algorithmSubmissionCount,
                ContestDtos.Overview::choiceSubmissionCount, ContestDtos.Overview::docxSubmissionCount)
                .containsExactly(2, 1, 1, 3, 1, 1, 1);
        ContestDtos.ProblemAnalytics algorithmMetrics = result.problems().stream()
                .filter(item -> item.problemType().equals("ALGORITHM")).findFirst().orElseThrow();
        assertThat(algorithmMetrics).extracting(ContestDtos.ProblemAnalytics::submissionCount,
                ContestDtos.ProblemAnalytics::acceptedSubmissionCount,
                ContestDtos.ProblemAnalytics::infrastructureFailureCount,
                ContestDtos.ProblemAnalytics::successParticipantCount)
                .containsExactly(1, 1, 1, 1);
        ContestDtos.ProblemAnalytics docxMetrics = result.problems().stream()
                .filter(item -> item.problemType().equals("OFFICE_DOCX")).findFirst().orElseThrow();
        assertThat(docxMetrics).extracting(ContestDtos.ProblemAnalytics::scoredParticipantCount,
                ContestDtos.ProblemAnalytics::averageBestScore, ContestDtos.ProblemAnalytics::medianBestScore,
                ContestDtos.ProblemAnalytics::needsReviewSubmissionCount)
                .containsExactly(1, 56.0d, 56.0d, 1);
        assertThat(result.timeline().stream().mapToInt(ContestDtos.TimelineBucket::submissionCount).sum()).isEqualTo(3);
        assertThat(result.overview().firstSubmissionAt()).isEqualTo(START.plusSeconds(60));
        assertThat(result.overview().lastSubmissionAt()).isEqualTo(START.plusSeconds(80));
        assertThat(result.distribution().stream().mapToInt(ContestDtos.DistributionBucket::participantCount).sum()).isEqualTo(2);

        ContestDtos.AnalyticsParticipants page = analytics.participants(contest, 1, 20, "");
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.participants()).extracting(ContestDtos.AnalyticsParticipant::username,
                ContestDtos.AnalyticsParticipant::totalSubmissionCount).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("student", 3), org.assertj.core.groups.Tuple.tuple("inactive", 0));
    }

    @Test
    void analyticsIsManagerOnlyAndManagerViewRemainsLiveAfterFreeze() {
        int contest = contest("SCORE", START.plusSeconds(45), START.plusSeconds(60));
        long problem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, student);
        algorithmSubmission(student, problem, "AC", START.plusSeconds(90));
        now(START.plusSeconds(100));

        as(student, "student", "USER");
        assertThatThrownBy(() -> analytics.analytics(contest)).hasMessageContaining("无权管理该比赛");
        CurrentUser.clear();
        assertThatThrownBy(() -> analytics.analytics(contest)).hasMessageContaining("请先登录");
        as(owner, "owner", "TEACHER");
        assertThat(analytics.analytics(contest).overview().totalSubmissionCount()).isEqualTo(1);
        int otherTeacher = user("other", "TEACHER");
        as(otherTeacher, "other", "TEACHER");
        assertThatThrownBy(() -> analytics.participants(contest, 1, 20, "student")).hasMessageContaining("无权管理该比赛");
        int admin = user("admin", "ADMIN");
        as(admin, "admin", "ADMIN");
        assertThat(analytics.participants(contest, 1, 20, "student").participants()).hasSize(1);
    }

    @Test
    void icpcAnalyticsUsesStandingsRankAndStableParticipantPaging() {
        int contest = contest("ICPC", null, null);
        long problem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, student);
        participant(contest, inactive);
        algorithmSubmission(student, problem, "WA", START.plusSeconds(60));
        algorithmSubmission(student, problem, "AC", START.plusSeconds(35 * 60));
        as(owner, "owner", "TEACHER");

        ContestDtos.Analytics result = analytics.analytics(contest);
        assertThat(result.overview()).extracting(ContestDtos.Overview::averageSolved,
                ContestDtos.Overview::maxSolved, ContestDtos.Overview::averagePenaltyAmongSolvedParticipants)
                .containsExactly(0.5d, 1, 55.0d);
        assertThat(result.distribution()).extracting(ContestDtos.DistributionBucket::label,
                ContestDtos.DistributionBucket::participantCount).containsExactly(
                org.assertj.core.groups.Tuple.tuple("0 solved", 1), org.assertj.core.groups.Tuple.tuple("1 solved", 1));
        List<ContestDtos.AnalyticsParticipant> rows = analytics.participants(contest, 1, 1, "").participants();
        assertThat(rows).singleElement().extracting(ContestDtos.AnalyticsParticipant::username,
                ContestDtos.AnalyticsParticipant::rank, ContestDtos.AnalyticsParticipant::solved,
                ContestDtos.AnalyticsParticipant::penaltyMinutes).containsExactly("student", 1, 1, 55);
    }

    @Test
    void rejudgeGenerationsDoNotAddSubmissionsAndKeepLastValidOutcome() {
        int contest = contest("SCORE", START.plusSeconds(45), null);
        long problem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, student);
        int submission = algorithmSubmission(student, problem, "JUDGE_FAILED", START.plusSeconds(60));
        jdbc.update("UPDATE \"Submission\" SET judge_generation=5 WHERE id=?", submission);
        for (int generation = 0; generation <= 5; generation++) {
            String verdict = generation == 4 ? "AC" : generation == 5 ? "JUDGE_FAILED" : "WA";
            jdbc.update("""
                    INSERT INTO algorithm_judge_history
                    (submission_id, judge_generation, verdict, passed, total, time_ms, memory_kb, message)
                    VALUES (?, ?, ?, 1, 1, 1, 1, 'history')
                    """, submission, generation, verdict);
        }
        as(owner, "owner", "TEACHER");
        ContestDtos.ProblemAnalytics afterFailure = analytics.analytics(contest).problems().get(0);
        assertThat(afterFailure).extracting(ContestDtos.ProblemAnalytics::submissionCount,
                ContestDtos.ProblemAnalytics::acceptedSubmissionCount,
                ContestDtos.ProblemAnalytics::successParticipantCount,
                ContestDtos.ProblemAnalytics::infrastructureFailureCount).containsExactly(1, 1, 1, 1);
        jdbc.update("UPDATE \"Submission\" SET verdict='PENDING' WHERE id=?", submission);
        ContestDtos.ProblemAnalytics whileRejudging = analytics.analytics(contest).problems().get(0);
        assertThat(whileRejudging).extracting(ContestDtos.ProblemAnalytics::submissionCount,
                ContestDtos.ProblemAnalytics::acceptedSubmissionCount,
                ContestDtos.ProblemAnalytics::successParticipantCount).containsExactly(1, 1, 1);
    }

    @Test
    void docxBestMedianAndParticipantPaginationUseAllRegisteredUsers() {
        int contest = contest("SCORE", START.plusSeconds(45), null);
        int exercise = exercise();
        long problem = problem(contest, "OFFICE_DOCX", null, null, exercise, "A");
        int[] scores = {70, 80, 90, 100};
        for (int index = 0; index < scores.length; index++) {
            int user = user("docx-" + index, "USER"); participant(contest, user);
            jdbc.update("""
                    INSERT INTO \"OfficeDocSubmission\" (user_id, exercise_id, contest_problem_id,
                    student_doc_path, student_doc_name, status, score, created_at)
                    VALUES (?, ?, ?, '/tmp/a.docx', 'a.docx', 'NEEDS_REVIEW', ?, ?)
                    """,
                    user, exercise, problem, scores[index], local(START.plusSeconds(60 + index)));
        }
        for (int index = 0; index < 51; index++) participant(contest, user("page-user-" + index, "USER"));
        as(owner, "owner", "TEACHER");
        ContestDtos.ProblemAnalytics metric = analytics.analytics(contest).problems().get(0);
        assertThat(metric).extracting(ContestDtos.ProblemAnalytics::scoredParticipantCount,
                ContestDtos.ProblemAnalytics::averageBestScore, ContestDtos.ProblemAnalytics::medianBestScore,
                ContestDtos.ProblemAnalytics::perfectScoreParticipantCount).containsExactly(4, 85.0d, 85.0d, 1);
        ContestDtos.AnalyticsParticipants first = analytics.participants(contest, 1, 500, "");
        ContestDtos.AnalyticsParticipants second = analytics.participants(contest, 2, 50, "");
        assertThat(first.pageSize()).isEqualTo(50); assertThat(first.total()).isEqualTo(55); assertThat(first.participants()).hasSize(50);
        assertThat(second.participants()).hasSize(5);
        assertThat(first.participants()).extracting(ContestDtos.AnalyticsParticipant::userId)
                .doesNotContainAnyElementsOf(second.participants().stream().map(ContestDtos.AnalyticsParticipant::userId).toList());
        assertThat(analytics.participants(contest, 1, 20, "docx-1").participants()).singleElement()
                .extracting(ContestDtos.AnalyticsParticipant::username).isEqualTo("docx-1");
    }

    @Test
    void analyticsEndpointsEnforceOwnerAdminAndNeverExposeSensitivePayloads() throws Exception {
        int contest = contest("SCORE", START.plusSeconds(45), null);
        long problem = problem(contest, "ALGORITHM", algorithm, null, null, "A");
        participant(contest, student); algorithmSubmission(student, problem, "AC", START.plusSeconds(60));
        String ownerToken = token(owner, "owner", "TEACHER");
        String studentToken = token(student, "student", "USER");
        int otherTeacher = user("other-teacher", "TEACHER");
        String otherToken = token(otherTeacher, "other-teacher", "TEACHER");
        int admin = user("admin", "ADMIN");
        String adminToken = token(admin, "admin", "ADMIN");
        String body = mockMvc.perform(get("/api/contests/{id}/analytics", contest).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("teacherText", "compareResult", "autoResult", "correctAnswer", "explanation", "sourceCode", "storagePath");
        mockMvc.perform(get("/api/contests/{id}/analytics/participants", contest).header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/contests/{id}/analytics", contest).header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/contests/{id}/analytics", contest).header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/contests/{id}/analytics/participants", contest).header("Authorization", "Bearer " + otherToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/contests/{id}/analytics", contest)).andExpect(status().isUnauthorized());
    }

    @Test
    void analyticsLoadsTitlesOnlyForProblemTypesPresentInTheContest() throws Exception {
        String ownerToken = token(owner, "owner", "TEACHER");
        int algorithmOnly = contest("SCORE", START.plusSeconds(45), null);
        problem(algorithmOnly, "ALGORITHM", algorithm, null, null, "A");

        int choiceOnly = contest("SCORE", START.plusSeconds(45), null);
        problem(choiceOnly, "OFFICE_CHOICE", null, question(), null, "A");

        int docxOnly = contest("SCORE", START.plusSeconds(45), null);
        problem(docxOnly, "OFFICE_DOCX", null, null, exercise(), "A");

        int mixed = contest("SCORE", START.plusSeconds(45), null);
        problem(mixed, "ALGORITHM", algorithm, null, null, "A");
        problem(mixed, "OFFICE_CHOICE", null, question(), null, "B");
        problem(mixed, "OFFICE_DOCX", null, null, exercise(), "C");

        for (int contestId : List.of(algorithmOnly, choiceOnly, docxOnly, mixed)) {
            mockMvc.perform(get("/api/contests/{id}/analytics", contestId)
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void bulkAggregatesOneHundredParticipantsAcrossTenProblems() {
        int contest = contest("SCORE", START.plusSeconds(45), null);
        long[] problems = new long[10];
        int[] algorithmProblems = new int[10];
        for (int index = 0; index < problems.length; index++) {
            algorithmProblems[index] = algorithmProblem("analytics-load-" + index);
            problems[index] = problem(contest, "ALGORITHM", algorithmProblems[index], null, null,
                    String.valueOf((char) ('A' + index)));
        }
        for (int userIndex = 0; userIndex < 100; userIndex++) {
            int user = user("load-user-" + userIndex, "USER");
            participant(contest, user);
            for (int problemIndex = 0; problemIndex < problems.length; problemIndex++) {
                algorithmSubmission(user, algorithmProblems[problemIndex], problems[problemIndex], "AC",
                        START.plusSeconds(60L + userIndex * 10L + problemIndex));
            }
        }
        as(owner, "owner", "TEACHER");
        ContestDtos.Analytics result = analytics.analytics(contest);
        assertThat(result.overview()).extracting(ContestDtos.Overview::participantCount,
                ContestDtos.Overview::activeParticipantCount, ContestDtos.Overview::totalSubmissionCount)
                .containsExactly(100, 100, 1000);
        assertThat(result.problems()).hasSize(10).allSatisfy(metric ->
                assertThat(metric).extracting(ContestDtos.ProblemAnalytics::submissionCount,
                        ContestDtos.ProblemAnalytics::uniqueSubmitterCount,
                        ContestDtos.ProblemAnalytics::successParticipantCount)
                        .containsExactly(100, 100, 100));
        assertThat(analytics.participants(contest, 1, 50, "")).extracting(
                ContestDtos.AnalyticsParticipants::total, ContestDtos.AnalyticsParticipants::pageSize)
                .containsExactly(100L, 50);
    }

    private int contest(String mode, Instant now, Instant freezeAt) {
        if (now != null) now(now);
        return jdbc.queryForObject("""
                INSERT INTO "Contest" (title, status, access_type, scoring_mode, owner_id, start_at, end_at, freeze_at)
                VALUES ('analytics', 'PUBLISHED', 'OPEN', ?, ?, ?, ?, ?) RETURNING id
                """, Integer.class, mode, owner, Timestamp.from(START), Timestamp.from(END), freezeAt == null ? null : Timestamp.from(freezeAt));
    }
    private long problem(int contest, String type, Integer algorithmId, Integer questionId, Integer exerciseId, String label) {
        return jdbc.queryForObject("""
                INSERT INTO "ContestProblem" (contest_id, problem_type, algorithm_problem_id, office_question_id,
                    office_exercise_id, display_order, label) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, contest, type, algorithmId, questionId, exerciseId, label.charAt(0) - 'A' + 1, label);
    }
    private int algorithmSubmission(int user, long contestProblem, String verdict, Instant at) {
        return algorithmSubmission(user, algorithm, contestProblem, verdict, at);
    }
    private int algorithmSubmission(int user, int algorithmProblemId, long contestProblem, String verdict, Instant at) {
        return jdbc.queryForObject("""
                INSERT INTO "Submission" (user_id, problem_id, contest_problem_id, language, code, verdict, passed, total, created_at)
                VALUES (?, ?, ?, 'python', 'print(1)', ?, 1, 1, ?) RETURNING id
                """, Integer.class, user, algorithmProblemId, contestProblem, verdict, local(at));
    }
    private int algorithmProblem(String slug) { return jdbc.queryForObject("""
            INSERT INTO "Problem" (slug, title, description, test_cases, created_by, visible, content_visibility)
            VALUES (?, ?, 'x', '[]', ?, true, 'PUBLIC') RETURNING id
            """, Integer.class, slug, slug, owner); }
    private int question() { return jdbc.queryForObject("""
            INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, answer, created_by)
            VALUES ('WORD', 'x', 'EASY', 'TRUE_FALSE', 'Choice analytics', 'T', ?) RETURNING id
            """, Integer.class, owner); }
    private int exercise() { return jdbc.queryForObject("""
            INSERT INTO "OfficeExercise" (title, description, teacher_doc_path, teacher_doc_name, starter_doc_path, starter_doc_name, created_by)
            VALUES ('Doc analytics', 'x', '/tmp/reference.docx', 'reference.docx', '/tmp/starter.docx', 'starter.docx', ?) RETURNING id
            """, Integer.class, owner); }
    private int user(String username, String role) { return jdbc.queryForObject("INSERT INTO \"User\" (username, password, role) VALUES (?, 'hash', ?) RETURNING id", Integer.class, username, role); }
    private void participant(int contest, int user) { jdbc.update("INSERT INTO \"ContestParticipant\" (contest_id, user_id) VALUES (?, ?)", contest, user); }
    private void as(int id, String username, String role) { CurrentUser.set(id, username, role); }
    @Autowired private com.oj.common.JwtUtil jwt;
    private String token(int id, String username, String role) { return jwt.sign(id, username, role, 0); }
    private void now(Instant time) { when(clock.instant()).thenReturn(time); }
    private static LocalDateTime local(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
}
