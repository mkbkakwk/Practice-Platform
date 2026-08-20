package com.oj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.config.AppProperties;
import com.oj.office.OfficeFileValidator;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfficeStudentResultRedactionIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String SENTINEL = "SECRET_TEACHER_REFERENCE_9f82c7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppProperties appProperties;

    private Path storageRoot;

    @BeforeEach
    void resetState() throws Exception {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "ContestProblem", "ContestParticipant", "Contest",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        storageRoot = Path.of(appProperties.getDocStorage()).toAbsolutePath().normalize();
        assertThat(storageRoot.toString()).contains("practice-platform-test-docs");
        cleanStorage();
        Files.createDirectories(storageRoot);
    }

    @AfterEach
    void cleanState() throws Exception {
        cleanStorage();
        Files.createDirectories(storageRoot);
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "ContestProblem", "ContestParticipant", "Contest",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @Test
    void ordinaryDocSubmitStudentDetailAndListResponsesAreRedacted() throws Exception {
        TestUser teacher = createUser("red_teacher", "TEACHER");
        TestUser student = createUser("red_student", "USER");
        TestUser otherStudent = createUser("red_other", "USER");
        int exerciseId = createExercise(teacher.id(), "PUBLIC");

        MvcResult submitted = submitOrdinary(exerciseId, student, "student answer");
        assertStudentResponse(submitted);
        int submissionId = submissionId(submitted);
        seedUnsafeInternalComparison(submissionId);

        MvcResult ownDetail = mockMvc.perform(get("/api/office/docs/submissions/{id}", submissionId)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andReturn();
        assertStudentResponse(ownDetail);
        JsonNode safeItems = objectMapper.readTree(body(ownDetail))
                .path("submission").path("resultDetail").path("items");
        assertThat(safeItems.path(0).path("expected").asText()).isEqualTo("参考内容不公开");
        assertThat(safeItems.path(1).path("expected").asText()).isEqualTo("宋体");
        assertThat(safeItems.path(1).path("actual").asText()).isEqualTo("微软雅黑");
        assertThat(safeItems.path(2).path("ruleId").asText()).isEqualTo("unsupported-rule");
        assertThat(safeItems.path(2).path("expected").asText()).isEqualTo("参考内容不公开");

        MvcResult ownList = mockMvc.perform(get("/api/office/docs/submissions")
                        .param("exerciseId", String.valueOf(exerciseId))
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andReturn();
        assertRedacted(ownList);

        MvcResult forbidden = mockMvc.perform(get("/api/office/docs/submissions/{id}", submissionId)
                        .header("Authorization", bearer(otherStudent)))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(body(forbidden)).doesNotContain(SENTINEL);
    }

    @Test
    void reviewerDetailIsRestrictedToOwnerTeacherAndAdmin() throws Exception {
        TestUser ownerTeacher = createUser("rev_owner", "TEACHER");
        TestUser otherTeacher = createUser("rev_other", "TEACHER");
        TestUser admin = createUser("rev_admin", "ADMIN");
        TestUser student = createUser("rev_student", "USER");
        int exerciseId = createExercise(ownerTeacher.id(), "PUBLIC");
        int submissionId = submissionId(submitOrdinary(exerciseId, student, "student answer"));
        seedUnsafeInternalComparison(submissionId);

        MvcResult ownerView = mockMvc.perform(get("/api/office/docs/submissions/{id}/review-detail", submissionId)
                        .header("Authorization", bearer(ownerTeacher)))
                .andExpect(status().isOk())
                .andReturn();
        assertReviewerResponse(ownerView);

        MvcResult reviewed = mockMvc.perform(put("/api/office/docs/submissions/{id}/review", submissionId)
                        .header("Authorization", bearer(ownerTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":80,\"comment\":\"checked\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertReviewerResponse(reviewed);

        MvcResult adminView = mockMvc.perform(get("/api/office/docs/submissions/{id}/review-detail", submissionId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();
        assertReviewerResponse(adminView);

        MvcResult otherTeacherView = mockMvc.perform(
                        get("/api/office/docs/submissions/{id}/review-detail", submissionId)
                                .header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(body(otherTeacherView)).doesNotContain(SENTINEL);

        MvcResult studentView = mockMvc.perform(
                        get("/api/office/docs/submissions/{id}/review-detail", submissionId)
                                .header("Authorization", bearer(student)))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(body(studentView)).doesNotContain(SENTINEL);
    }

    @Test
    void contestDocSubmitResponseIsRedacted() throws Exception {
        TestUser teacher = createUser("contest_teacher", "TEACHER");
        TestUser student = createUser("contest_student", "USER");
        int exerciseId = createExercise(teacher.id(), "CONTEST_ONLY");
        int contestId = jdbcTemplate.queryForObject("""
                INSERT INTO "Contest" (title, description, status, access_type, owner_id, start_at, end_at)
                VALUES ('Redaction contest', '', 'PUBLISHED', 'INVITE_ONLY', ?,
                        NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 hour')
                RETURNING id
                """, Integer.class, teacher.id());
        long contestProblemId = jdbcTemplate.queryForObject("""
                INSERT INTO "ContestProblem"
                    (contest_id, problem_type, office_exercise_id, display_order, label)
                VALUES (?, 'OFFICE_DOCX', ?, 1, 'A') RETURNING id
                """, Long.class, contestId, exerciseId);
        jdbcTemplate.update("""
                INSERT INTO "ContestParticipant" (contest_id, user_id, added_by)
                VALUES (?, ?, ?)
                """, contestId, student.id(), teacher.id());

        MvcResult submitted = mockMvc.perform(multipart(
                                "/api/contests/{contestId}/problems/{contestProblemId}/office-submissions",
                                contestId, contestProblemId)
                        .file(document("student.docx", "student answer"))
                        .header("Authorization", bearer(student)))
                .andExpect(status().isCreated())
                .andReturn();
        assertStudentResponse(submitted);
    }

    private MvcResult submitOrdinary(int exerciseId, TestUser student, String text) throws Exception {
        return mockMvc.perform(multipart("/api/office/docs/exercises/{id}/submit", exerciseId)
                        .file(document("student.docx", text))
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int createExercise(int teacherId, String visibility) throws Exception {
        String referenceStorageId = UUID.randomUUID() + ".docx";
        String starterStorageId = UUID.randomUUID() + ".docx";
        Files.write(storageRoot.resolve(referenceStorageId), docx(SENTINEL));
        Files.write(storageRoot.resolve(starterStorageId), docx("STUDENT_STARTER_DOCUMENT"));
        return jdbcTemplate.queryForObject("""
                INSERT INTO "OfficeExercise"
                    (title, difficulty, description, starter_doc_path, starter_doc_name,
                     teacher_doc_path, teacher_doc_name, visible, created_by, content_visibility)
                VALUES ('Redaction exercise', 'EASY', 'security regression', ?, 'starter.docx',
                        ?, 'reference.docx', TRUE, ?, ?)
                RETURNING id
                """, Integer.class, starterStorageId, referenceStorageId, teacherId, visibility);
    }

    private void seedUnsafeInternalComparison(int submissionId) {
        String comparison = """
                [{"index":0,"studentText":"student answer","teacherText":"%s",
                  "diffs":[{"ruleId":"paragraph-0-text","label":"文字内容",
                  "student":"student answer","teacher":"%s","match":false}],"match":false}]
                """.formatted(SENTINEL, SENTINEL);
        String detail = """
                {"judgeVersion":"office-docx-v1","totalScore":100,"earnedScore":0,
                 "passed":false,"totalErrorCount":3,"truncated":false,
                 "futureReferenceText":"%s",
                 "items":[{"ruleId":"paragraph-0-text","target":"%s",
                 "expected":"%s","actual":"student answer","score":1,"earned":0,
                 "passed":false,"message":"%s"},
                 {"ruleId":"paragraph-0-run-0-font","target":"第1段第1文本片段",
                 "expected":"宋体","actual":"微软雅黑","score":1,"earned":0,
                 "passed":false,"message":"字体不一致"},
                 {"ruleId":"future-reference-rule","target":"%s",
                 "expected":"%s","actual":"student answer","score":1,"earned":0,
                 "passed":false,"message":"%s"}],
                 "comparisonRows":%s}
                """.formatted(SENTINEL, SENTINEL, SENTINEL, SENTINEL,
                SENTINEL, SENTINEL, SENTINEL, comparison);
        jdbcTemplate.update("""
                UPDATE "OfficeDocSubmission"
                SET compare_result=?, result_detail=CAST(? AS jsonb)
                WHERE id=?
                """, comparison, detail, submissionId);
    }

    private void assertStudentResponse(MvcResult result) throws Exception {
        JsonNode submission = objectMapper.readTree(body(result)).path("submission");
        assertThat(submission.isObject()).isTrue();
        assertThat(submission.has("compareResult")).isFalse();
        assertThat(submission.has("autoResult")).isFalse();
        assertThat(submission.path("resultDetail").has("comparisonRows")).isFalse();
        assertRedacted(result);
    }

    private void assertRedacted(MvcResult result) throws Exception {
        assertThat(body(result))
                .doesNotContain(SENTINEL)
                .doesNotContain("teacherText")
                .doesNotContain("compareResult")
                .doesNotContain("comparisonRows");
    }

    private void assertReviewerResponse(MvcResult result) throws Exception {
        assertThat(body(result)).contains("compareResult", "teacherText", SENTINEL);
    }

    private int submissionId(MvcResult result) throws Exception {
        return objectMapper.readTree(body(result))
                .path("submission").path("id").asInt();
    }

    private TestUser createUser(String username, String role) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        int id = objectMapper.readTree(body(registered))
                .path("user").path("id").asInt();
        if (!"USER".equals(role)) {
            jdbcTemplate.update("UPDATE \"User\" SET role=? WHERE id=?", role, id);
        }
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new TestUser(id, objectMapper.readTree(body(login))
                .path("token").asText());
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MockMultipartFile document(String filename, String text) throws Exception {
        return new MockMultipartFile("file", filename, OfficeFileValidator.DOCX_CONTENT_TYPE, docx(text));
    }

    private byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private void cleanStorage() throws Exception {
        if (storageRoot == null || !Files.exists(storageRoot)) return;
        try (var paths = Files.walk(storageRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(storageRoot)) Files.deleteIfExists(path);
            }
        }
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private record TestUser(int id, String token) {}
}
