package com.oj;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.ContestException;
import com.oj.dto.*;
import com.oj.entity.ContestProblemEntity;
import com.oj.entity.OfficeDocSubmissionEntity;
import com.oj.mapper.ContestParticipantMapper;
import com.oj.mapper.ContestProblemMapper;
import com.oj.mapper.OfficeDocSubmissionMapper;
import com.oj.mapper.SubmissionMapper;
import com.oj.service.ContestService;
import com.oj.service.OfficeDocService;
import com.oj.service.ProblemService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ContestCoreIntegrationTest {
    private static final Instant START = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContestService contests;
    @Autowired private ContestParticipantMapper participants;
    @Autowired private ContestProblemMapper contestProblems;
    @Autowired private SubmissionMapper submissions;
    @Autowired private OfficeDocSubmissionMapper officeSubmissions;
    @Autowired private OfficeDocService officeDocs;
    @Autowired private ProblemService problems;
    @MockBean private Clock clock;

    private int teacher;
    private int otherTeacher;
    private int admin;
    private int student;
    private int otherStudent;
    private int algorithmProblem;
    private int contestOnlyProblem;
    private int officeExercise;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "ContestProblem", "ContestParticipant", "Contest",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        teacher = user("teacher", "TEACHER");
        otherTeacher = user("other-teacher", "TEACHER");
        admin = user("admin", "ADMIN");
        student = user("student", "USER");
        otherStudent = user("other-student", "USER");
        algorithmProblem = problem("public-problem", teacher, "PUBLIC");
        contestOnlyProblem = problem("secret-problem", teacher, "CONTEST_ONLY");
        officeExercise = exercise(teacher, "CONTEST_ONLY");
        setNow(START.minusSeconds(60));
    }

    @AfterEach
    void clearUser() {
        CurrentUser.clear();
    }

    @Test
    void ownershipLifecycleAndVisibilityAreEnforced() {
        as(student, "student", "USER");
        assertThatThrownBy(() -> contests.create(request("blocked", "OPEN")))
                .isInstanceOf(ApiException.class);

        as(teacher, "teacher", "TEACHER");
        int contestId = contests.create(request("Core Contest", "OPEN")).getId();
        ContestProblemRequest add = problemRequest("ALGORITHM", contestOnlyProblem);
        ContestDtos.ProblemItem item = contests.addProblem(contestId, add);

        as(otherTeacher, "other-teacher", "TEACHER");
        assertThatThrownBy(() -> contests.update(contestId, request("hijack", "OPEN")))
                .isInstanceOf(ContestException.class);

        as(admin, "admin", "ADMIN");
        assertThat(contests.update(contestId, request("Admin update", "OPEN")).getTitle())
                .isEqualTo("Admin update");

        as(student, "student", "USER");
        assertThatThrownBy(() -> contests.detail(contestId)).isInstanceOf(ContestException.class);

        CurrentUser.clear();
        assertThatThrownBy(() -> problems.getBySlug("secret-problem")).isInstanceOf(ApiException.class);

        as(teacher, "teacher", "TEACHER");
        contests.publish(contestId);
        as(student, "student", "USER");
        contests.join(contestId);
        assertThat(contests.detail(contestId).problems()).isEmpty();
        assertThatThrownBy(() -> problems.getBySlug("secret-problem")).isInstanceOf(ApiException.class);

        setNow(START);
        assertThat(contests.detail(contestId).problems()).extracting(ContestDtos.ProblemItem::contestProblemId)
                .containsExactly(item.contestProblemId());
        assertThat(problems.getBySlug("secret-problem").getId()).isEqualTo(contestOnlyProblem);

        as(otherStudent, "other-student", "USER");
        assertThatThrownBy(() -> problems.getBySlug("secret-problem")).isInstanceOf(ApiException.class);
        setNow(END);
        as(student, "student", "USER");
        assertThat(problems.getBySlug("secret-problem").getId()).isEqualTo(contestOnlyProblem);
    }

    @Test
    void managementAndTerminalLifecycleMatrixIsEnforced() {
        as(teacher, "teacher", "TEACHER");
        int empty = contests.create(request("empty", "OPEN")).getId();
        assertThatThrownBy(() -> contests.publish(empty))
                .isInstanceOf(ContestException.class).hasMessageContaining("至少需要一道题目");
        assertThat(contests.delete(empty)).containsEntry("deleted", true);
        assertThatThrownBy(() -> contests.detail(empty)).isInstanceOf(ContestException.class);

        int protectedContest = contests.create(request("protected", "OPEN")).getId();
        contests.addProblem(protectedContest, problemRequest("ALGORITHM", algorithmProblem));
        contests.publish(protectedContest);
        assertThatThrownBy(() -> contests.delete(protectedContest)).isInstanceOf(ContestException.class);

        as(otherTeacher, "other-teacher", "TEACHER");
        assertThatThrownBy(() -> contests.cancel(protectedContest)).isInstanceOf(ContestException.class);

        as(admin, "admin", "ADMIN");
        assertThat(contests.cancel(protectedContest).getStatus()).isEqualTo("CANCELLED");
        assertThat(contests.detail(protectedContest).contest().phase()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> contests.update(protectedContest, request("revive", "OPEN")))
                .isInstanceOf(ContestException.class);

        int adminContest = contests.create(request("admin-owned", "INVITE_ONLY")).getId();
        assertThat(contests.detail(adminContest).contest().ownerId()).isEqualTo(admin);
    }

    @Test
    void inviteRosterOwnershipAndOfficeVisibilityAreEnforced() throws Exception {
        as(teacher, "teacher", "TEACHER");
        int contestId = contests.create(request("invite", "INVITE_ONLY")).getId();
        contests.addProblem(contestId, problemRequest("ALGORITHM", algorithmProblem));
        ContestDtos.ProblemItem office = contests.addProblem(contestId, problemRequest("OFFICE", officeExercise));
        officeDocs.uploadTeacherDoc(officeExercise, docx("reference"));

        as(otherTeacher, "other-teacher", "TEACHER");
        assertThatThrownBy(() -> contests.addParticipant(contestId, student)).isInstanceOf(ContestException.class);
        int otherOwned = contests.create(request("other-owned", "OPEN")).getId();
        assertThatThrownBy(() -> contests.addProblem(otherOwned, problemRequest("ALGORITHM", algorithmProblem)))
                .isInstanceOf(ContestException.class);

        as(teacher, "teacher", "TEACHER");
        assertThat(contests.addParticipant(contestId, student).userId()).isEqualTo(student);
        assertThat(contests.removeParticipant(contestId, student)).containsEntry("removed", true);

        as(admin, "admin", "ADMIN");
        assertThat(contests.addParticipant(contestId, student).userId()).isEqualTo(student);

        as(teacher, "teacher", "TEACHER");
        contests.publish(contestId);
        as(student, "student", "USER");
        assertThatThrownBy(() -> contests.join(contestId)).isInstanceOf(ContestException.class);
        assertThatThrownBy(() -> officeDocs.getExercise(officeExercise)).isInstanceOf(ApiException.class);
        setNow(START);
        assertThat(officeDocs.getExercise(officeExercise).getId()).isEqualTo(officeExercise);
        assertThat(contests.detail(contestId).problems()).extracting(ContestDtos.ProblemItem::contestProblemId)
                .contains(office.contestProblemId());

        as(otherStudent, "other-student", "USER");
        assertThatThrownBy(() -> contests.detail(contestId)).isInstanceOf(ContestException.class);
        assertThatThrownBy(() -> officeDocs.getExercise(officeExercise)).isInstanceOf(ApiException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void contentManagersCanSearchOnlySafeStudentIdentityFields() {
        as(teacher, "teacher", "TEACHER");
        var result = contests.students("student", 1, 20);
        assertThat(result).containsEntry("total", 2L).containsEntry("page", 1).containsEntry("pageSize", 20);
        var options = (List<ContestDtos.StudentOption>) result.get("students");
        assertThat(options).extracting(ContestDtos.StudentOption::username)
                .containsExactly("other-student", "student");
        assertThat(options).allSatisfy(option -> {
            assertThat(option.id()).isNotNull();
            assertThat(option.role()).isEqualTo("USER");
        });

        as(admin, "admin", "ADMIN");
        assertThat((List<ContestDtos.StudentOption>) contests.students("other", 1, 20).get("students"))
                .extracting(ContestDtos.StudentOption::username).containsExactly("other-student");

        as(student, "student", "USER");
        assertThatThrownBy(() -> contests.students("", 1, 20)).isInstanceOf(ApiException.class);
    }

    @Test
    void participantAndProblemMutationsAreDeterministic() throws Exception {
        as(teacher, "teacher", "TEACHER");
        int contestId = contests.create(request("Concurrent join", "OPEN")).getId();
        ContestDtos.ProblemItem first = contests.addProblem(contestId, problemRequest("ALGORITHM", algorithmProblem));
        ContestDtos.ProblemItem second = contests.addProblem(contestId, problemRequest("OFFICE", officeExercise));
        assertThatThrownBy(() -> contests.addProblem(contestId, problemRequest("ALGORITHM", algorithmProblem)))
                .isInstanceOf(ContestException.class);
        assertThat(contests.reorderProblems(contestId,
                List.of(second.contestProblemId(), first.contestProblemId())))
                .extracting(ContestDtos.ProblemItem::displayOrder).containsExactly(1, 2);

        officeDocs.uploadTeacherDoc(officeExercise, docx("reference"));
        contests.publish(contestId);
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                calls.add(() -> {
                    as(student, "student", "USER");
                    contests.join(contestId);
                    CurrentUser.clear();
                    return null;
                });
            }
            for (var result : executor.invokeAll(calls)) result.get();
        }
        assertThat(participants.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.oj.entity.ContestParticipantEntity>()
                .eq("contest_id", contestId).eq("user_id", student))).isEqualTo(1);

        setNow(START);
        as(teacher, "teacher", "TEACHER");
        assertThatThrownBy(() -> contests.removeParticipant(contestId, student))
                .isInstanceOf(ContestException.class);
        assertThatThrownBy(() -> contests.removeProblem(contestId, first.contestProblemId()))
                .isInstanceOf(ContestException.class);
    }

    @Test
    void algorithmSubmissionUsesClosedOpenWindowAndTransactionalOutbox() throws Exception {
        RunningContest fixture = runningContestWithAlgorithm();
        as(student, "student", "USER");
        ContestAlgorithmSubmitRequest request = algorithmSubmission();

        setNow(START.minusMillis(1));
        assertThatThrownBy(() -> contests.submitAlgorithm(fixture.contestId, fixture.problemId, request))
                .isInstanceOf(ContestException.class).hasMessageContaining("尚未开始");

        setNow(START);
        int atStart = contests.submitAlgorithm(fixture.contestId, fixture.problemId, request);
        setNow(END.minusMillis(1));
        int beforeEnd = contests.submitAlgorithm(fixture.contestId, fixture.problemId, request);
        assertThat(submissions.selectById(atStart).getContestProblemId()).isEqualTo(fixture.problemId);
        assertThat(submissions.selectById(beforeEnd).getContestProblemId()).isEqualTo(fixture.problemId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM judge_outbox WHERE submission_id IN (?, ?)",
                Long.class, atStart, beforeEnd)).isEqualTo(2);

        setNow(END);
        assertThatThrownBy(() -> contests.submitAlgorithm(fixture.contestId, fixture.problemId, request))
                .isInstanceOf(ContestException.class).hasMessageContaining("已结束");
        setNow(END.plusMillis(1));
        assertThatThrownBy(() -> contests.submitAlgorithm(fixture.contestId, fixture.problemId, request))
                .isInstanceOf(ContestException.class);

        as(otherStudent, "other-student", "USER");
        setNow(START);
        assertThatThrownBy(() -> contests.submitAlgorithm(fixture.contestId, fixture.problemId, request))
                .isInstanceOf(ContestException.class).hasMessageContaining("参赛者");
    }

    @Test
    void twentyConcurrentSubmissionsKeepTheirContestContext() throws Exception {
        RunningContest fixture = runningContestWithAlgorithm();
        setNow(START.plusSeconds(1));
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                calls.add(() -> {
                    as(student, "student", "USER");
                    int id = contests.submitAlgorithm(fixture.contestId, fixture.problemId, algorithmSubmission());
                    CurrentUser.clear();
                    return id;
                });
            }
            List<Integer> ids = new ArrayList<>();
            for (var result : executor.invokeAll(calls)) ids.add(result.get());
            assertThat(ids).hasSize(20).doesNotHaveDuplicates();
        }
        assertThat(submissions.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.oj.entity.SubmissionEntity>()
                .eq("contest_problem_id", fixture.problemId))).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM judge_outbox", Long.class)).isEqualTo(20);
    }

    @Test
    void docxContestSubmissionReusesSecureDeterministicJudge() throws Exception {
        as(teacher, "teacher", "TEACHER");
        officeDocs.uploadTeacherDoc(officeExercise, docx("expected"));
        int contestId = contests.create(request("DOCX contest", "INVITE_ONLY")).getId();
        ContestDtos.ProblemItem item = contests.addProblem(contestId, problemRequest("OFFICE", officeExercise));
        contests.addParticipant(contestId, student);
        contests.publish(contestId);
        setNow(START);
        as(student, "student", "USER");
        OfficeSubmissionDtos.StudentSubmission full =
                contests.submitOffice(contestId, item.contestProblemId(), docx("expected"));
        OfficeSubmissionDtos.StudentSubmission mismatch =
                contests.submitOffice(contestId, item.contestProblemId(), docx("different"));
        assertThat(full.score()).isEqualTo(100);
        assertThat(mismatch.score()).isLessThan(100);
        assertThat(officeSubmissions.selectById(full.id()).getContestProblemId())
                .isEqualTo(item.contestProblemId());

        MockMultipartFile invalid = new MockMultipartFile("file", "bad.docx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "not-a-docx".getBytes());
        assertThatThrownBy(() -> contests.submitOffice(contestId, item.contestProblemId(), invalid))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"OfficeDocSubmission\" WHERE status='FAILED' AND contest_problem_id=?",
                Long.class, item.contestProblemId())).isEqualTo(1);
    }

    @Test
    void databaseConstraintsProtectAssociationsAndImmutableContext() {
        as(teacher, "teacher", "TEACHER");
        int contestId = contests.create(request("constraints", "OPEN")).getId();
        ContestDtos.ProblemItem item = contests.addProblem(contestId, problemRequest("ALGORITHM", algorithmProblem));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO "ContestProblem" (contest_id, problem_type, algorithm_problem_id,
                    office_exercise_id, display_order)
                VALUES (?, 'ALGORITHM', NULL, ?, 9)
                """, contestId, officeExercise)).isInstanceOf(org.springframework.dao.DataAccessException.class);

        contests.publish(contestId);
        as(student, "student", "USER");
        contests.join(contestId);
        setNow(START);
        int submissionId = contests.submitAlgorithm(contestId, item.contestProblemId(), algorithmSubmission());
        assertThatThrownBy(() -> jdbc.update("UPDATE \"Submission\" SET contest_problem_id=NULL WHERE id=?", submissionId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void wrongContestProblemCannotBeInjected() {
        RunningContest first = runningContestWithAlgorithm();
        as(teacher, "teacher", "TEACHER");
        int secondContest = contests.create(request("second", "OPEN")).getId();
        ContestDtos.ProblemItem secondProblem = contests.addProblem(secondContest,
                problemRequest("ALGORITHM", contestOnlyProblem));
        contests.publish(secondContest);
        as(student, "student", "USER");
        contests.join(secondContest);
        setNow(START);
        assertThatThrownBy(() -> contests.submitAlgorithm(first.contestId,
                secondProblem.contestProblemId(), algorithmSubmission()))
                .isInstanceOf(ContestException.class).hasMessageContaining("不属于该比赛");
    }

    private RunningContest runningContestWithAlgorithm() {
        as(teacher, "teacher", "TEACHER");
        int contestId = contests.create(request("running", "OPEN")).getId();
        ContestDtos.ProblemItem item = contests.addProblem(contestId, problemRequest("ALGORITHM", algorithmProblem));
        contests.publish(contestId);
        as(student, "student", "USER");
        contests.join(contestId);
        return new RunningContest(contestId, item.contestProblemId());
    }

    private ContestUpsertRequest request(String title, String accessType) {
        ContestUpsertRequest request = new ContestUpsertRequest();
        request.setTitle(title);
        request.setDescription("Contest core test");
        request.setStartAt(START);
        request.setEndAt(END);
        request.setAccessType(accessType);
        return request;
    }

    private ContestProblemRequest problemRequest(String type, int problemId) {
        ContestProblemRequest request = new ContestProblemRequest();
        request.setProblemType(type);
        request.setProblemId(problemId);
        return request;
    }

    private ContestAlgorithmSubmitRequest algorithmSubmission() {
        ContestAlgorithmSubmitRequest request = new ContestAlgorithmSubmitRequest();
        request.setLanguage("python");
        request.setCode("print(1)");
        return request;
    }

    private int user(String username, String role) {
        return jdbc.queryForObject("""
                INSERT INTO "User" (username, password, role)
                VALUES (?, 'hash', ?) RETURNING id
                """, Integer.class, username, role);
    }

    private int problem(String slug, int owner, String visibility) {
        return jdbc.queryForObject("""
                INSERT INTO "Problem" (slug, title, description, test_cases, created_by, content_visibility)
                VALUES (?, ?, 'description', '[{"input":"","output":"1"}]', ?, ?) RETURNING id
                """, Integer.class, slug, slug, owner, visibility);
    }

    private int exercise(int owner, String visibility) {
        return jdbc.queryForObject("""
                INSERT INTO "OfficeExercise" (title, description, created_by, content_visibility)
                VALUES ('DOCX', 'description', ?, ?) RETURNING id
                """, Integer.class, owner, visibility);
    }

    private MockMultipartFile docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            document.write(output);
            return new MockMultipartFile("file", "submission.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", output.toByteArray());
        }
    }

    private void setNow(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private void as(int id, String username, String role) {
        CurrentUser.set(id, username, role);
    }

    private record RunningContest(int contestId, long problemId) {}
}
