package com.oj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformIntegrationTest {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        jdbcTemplate.queryForObject("""
                SELECT setval(pg_get_serial_sequence('"User"', 'id'), 999, true)
                """, Long.class);
    }


    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @Test
    void registrationLoginAndDisabledFirstAdminStayUser() throws Exception {
        JsonNode registered = register("student_auth");
        assertThat(registered.path("user").path("role").asText()).isEqualTo("USER");

        JsonNode loggedIn = login("student_auth");
        assertThat(loggedIn.path("token").asText()).isNotBlank();
        assertThat(loggedIn.path("user").path("role").asText()).isEqualTo("USER");
    }

    @Test
    void adminCanChangeAUserRole() throws Exception {
        TestUser admin = createUser("admin_role", "ADMIN");
        TestUser target = createUser("role_target", "USER");

        mockMvc.perform(put("/api/users/{id}/role", target.id())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"role\":\"TEACHER\"" + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("TEACHER"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM \"User\" WHERE id=?", String.class, target.id()))
                .isEqualTo("TEACHER");
    }

    @Test
    void userCannotCallAnyContentManagementEndpoint() throws Exception {
        TestUser user = createUser("plain_user", "USER");

        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/office/questions/manage").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/office/docs/exercises/manage").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void problemCreationRejectsNullEmptyAndMalformedTestCases() throws Exception {
        TestUser teacher = createUser("test_case_teacher", "TEACHER");

        ObjectNode nullPayload = problemPayloadNode("null-test-cases");
        nullPayload.putNull("testCases");
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullPayload.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("测试点不能为空，至少需要 1 个测试点"));

        ObjectNode emptyPayload = problemPayloadNode("empty-test-cases");
        emptyPayload.putArray("testCases");
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyPayload.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("测试点不能为空，至少需要 1 个测试点"));

        ObjectNode objectPayload = problemPayloadNode("object-test-cases");
        objectPayload.putObject("testCases");
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectPayload.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("测试点必须是 JSON 数组"));

        ObjectNode malformedPayload = problemPayloadNode("malformed-test-cases");
        malformedPayload.putArray("testCases").addObject().put("input", "");
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedPayload.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("第 1 个测试点必须只包含字符串 input 和 output"));

        assertThat(count("SELECT COUNT(*) FROM \"Problem\"")).isZero();
    }

    @Test
    void problemCreateAndUpdateAcceptNonEmptyCasesButUpdateRejectsEmptyCases() throws Exception {
        TestUser teacher = createUser("valid_case_teacher", "TEACHER");
        int problemId = createProblem(teacher, "valid-test-cases");

        ObjectNode validUpdate = problemPayloadNode("valid-test-cases");
        validUpdate.putArray("testCases")
                .addObject()
                .put("input", "2 3")
                .put("output", "5");
        mockMvc.perform(put("/api/problems/valid-test-cases")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate.toString()))
                .andExpect(status().isOk());

        String storedCases = jdbcTemplate.queryForObject(
                "SELECT test_cases FROM \"Problem\" WHERE id=?", String.class, problemId);
        assertThat(objectMapper.readTree(storedCases).path(0).path("output").asText()).isEqualTo("5");

        ObjectNode emptyUpdate = problemPayloadNode("valid-test-cases");
        emptyUpdate.putArray("testCases");
        mockMvc.perform(put("/api/problems/valid-test-cases")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyUpdate.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("测试点不能为空，至少需要 1 个测试点"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT test_cases FROM \"Problem\" WHERE id=?", String.class, problemId))
                .isEqualTo(storedCases);
    }

    @Test
    void problemTestCaseValidationDoesNotBypassManagementPermissions() throws Exception {
        TestUser owner = createUser("case_owner", "TEACHER");
        TestUser otherTeacher = createUser("case_other", "TEACHER");
        TestUser user = createUser("case_user", "USER");
        createProblem(owner, "permission-test-cases");

        ObjectNode emptyCreate = problemPayloadNode("user-empty-test-cases");
        emptyCreate.putArray("testCases");
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyCreate.toString()))
                .andExpect(status().isForbidden());

        ObjectNode emptyUpdate = problemPayloadNode("permission-test-cases");
        emptyUpdate.putArray("testCases");
        mockMvc.perform(put("/api/problems/permission-test-cases")
                        .header("Authorization", bearer(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyUpdate.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCreatesAndManagesOwnContentWithLargeIntegerId() throws Exception {
        TestUser teacher = createUser("teacher_owner", "TEACHER");
        assertThat(teacher.id()).isGreaterThan(127);

        int problemId = createProblem(teacher, "teacher-problem");
        int questionId = createQuestion(teacher, "Teacher question");
        int exerciseId = createExercise(teacher, "Teacher exercise");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM \"Problem\" WHERE id=?", Integer.class, problemId))
                .isEqualTo(teacher.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM \"OfficeQuestion\" WHERE id=?", Integer.class, questionId))
                .isEqualTo(teacher.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM \"OfficeExercise\" WHERE id=?", Integer.class, exerciseId))
                .isEqualTo(teacher.id());

        mockMvc.perform(put("/api/problems/{slug}/visibility", "teacher-problem")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/office/questions/{id}/visibility", questionId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/office/docs/exercises/{id}/visibility", exerciseId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void teacherCannotManageOtherTeacherOrSystemContent() throws Exception {
        TestUser owner = createUser("teacher_one", "TEACHER");
        TestUser other = createUser("teacher_two", "TEACHER");

        int problemId = createProblem(owner, "owned-problem");
        int questionId = createQuestion(owner, "Owned question");
        int exerciseId = createExercise(owner, "Owned exercise");

        mockMvc.perform(put("/api/problems/{slug}/visibility", "owned-problem")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/office/questions/{id}/visibility", questionId)
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/office/docs/exercises/{id}/visibility", exerciseId)
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());

        int systemQuestionId = jdbcTemplate.queryForObject("""
                INSERT INTO "OfficeQuestion"
                    (app_type, category, question_type, content, answer, visible, created_by)
                VALUES ('WORD', 'system', 'TRUE_FALSE', 'System question', 'T', true, NULL)
                RETURNING id
                """, Integer.class);
        int systemExerciseId = jdbcTemplate.queryForObject("""
                INSERT INTO "OfficeExercise" (title, description, visible, created_by)
                VALUES ('System exercise', 'system', true, NULL)
                RETURNING id
                """, Integer.class);
        jdbcTemplate.update("""
                INSERT INTO "Problem"
                    (slug, title, description, tags, samples, test_cases, visible, created_by)
                VALUES ('system-problem', 'System problem', 'system', '{}', '[]', '[]', true, NULL)
                """);

        mockMvc.perform(put("/api/problems/system-problem/visibility")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/office/questions/{id}/visibility", systemQuestionId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/office/docs/exercises/{id}/visibility", systemExerciseId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());

        assertThat(problemId).isPositive();
    }

    @Test
    void disabledContentRejectsNewStudentActivity() throws Exception {
        TestUser teacher = createUser("disable_teacher", "TEACHER");
        TestUser student = createUser("disable_student", "USER");
        int problemId = createProblem(teacher, "disabled-problem");
        int questionId = createQuestion(teacher, "Disabled question");
        int exerciseId = createExercise(teacher, "Disabled exercise");

        setVisible(teacher, "/api/problems/disabled-problem/visibility", false);
        setVisible(teacher, "/api/office/questions/" + questionId + "/visibility", false);
        setVisible(teacher, "/api/office/docs/exercises/" + exerciseId + "/visibility", false);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problemId":%d,"language":"python","code":"print(1)"}
                                """.formatted(problemId)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/office/submit")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId":%d,"selected":["0"]}
                                """.formatted(questionId)))
                .andExpect(status().isConflict());

        MockMultipartFile upload = new MockMultipartFile(
                "file", "student.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not-used-because-disabled".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/office/docs/exercises/{id}/submit", exerciseId)
                        .file(upload)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherCannotHardDeleteContentWithStudentRecords() throws Exception {
        TestUser teacher = createUser("delete_teacher", "TEACHER");
        TestUser student = createUser("delete_student", "USER");
        int problemId = createProblem(teacher, "blocked-delete");
        int questionId = createQuestion(teacher, "Blocked delete question");
        int exerciseId = createExercise(teacher, "Blocked delete exercise");

        jdbcTemplate.update("""
                INSERT INTO "Submission" (user_id, problem_id, language, code)
                VALUES (?, ?, 'python', 'print(1)')
                """, student.id(), problemId);
        jdbcTemplate.update("""
                INSERT INTO "OfficeRecord" (user_id, question_id, selected, correct)
                VALUES (?, ?, '["0"]', true)
                """, student.id(), questionId);
        jdbcTemplate.update("""
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name)
                VALUES (?, ?, '/tmp/practice-platform-test-docs/blocked.docx', 'blocked.docx')
                """, student.id(), exerciseId);

        assertTeacherDeleteBlocked(teacher, "/api/problems/blocked-delete");
        assertTeacherDeleteBlocked(teacher, "/api/office/questions/" + questionId);
        assertTeacherDeleteBlocked(teacher, "/api/office/docs/exercises/" + exerciseId);
    }

    @Test
    void adminDeletesProblemAndRecalculatesSolvedCountFromRemainingAcRows() throws Exception {
        TestUser admin = createUser("delete_admin", "ADMIN");
        TestUser student = createUser("score_student", "USER");
        int firstProblem = createProblem(admin, "delete-first");
        int secondProblem = createProblem(admin, "keep-second");

        jdbcTemplate.update("""
                INSERT INTO "Submission" (user_id, problem_id, language, code, verdict)
                VALUES (?, ?, 'python', 'print(1)', 'AC'),
                       (?, ?, 'python', 'print(1)', 'AC'),
                       (?, ?, 'python', 'print(1)', 'WA')
                """, student.id(), firstProblem, student.id(), secondProblem, student.id(), firstProblem);
        jdbcTemplate.update("UPDATE \"User\" SET solved_count=2 WHERE id=?", student.id());

        mockMvc.perform(delete("/api/problems/delete-first")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        assertThat(count("SELECT COUNT(*) FROM \"Problem\" WHERE id=?", firstProblem)).isZero();
        assertThat(count("SELECT COUNT(*) FROM \"Submission\" WHERE problem_id=?", firstProblem)).isZero();
        assertThat(count("SELECT COUNT(*) FROM \"Submission\" WHERE problem_id=?", secondProblem)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT solved_count FROM \"User\" WHERE id=?", Integer.class, student.id()))
                .isEqualTo(1);
    }

    @Test
    void submissionAndDocumentAccessAreScopedToTheirOwners() throws Exception {
        TestUser owner = createUser("submission_owner", "USER");
        TestUser stranger = createUser("submission_other", "USER");
        TestUser teacherOwner = createUser("doc_teacher_one", "TEACHER");
        TestUser teacherOther = createUser("doc_teacher_two", "TEACHER");
        TestUser admin = createUser("scope_admin", "ADMIN");

        int problemId = createProblem(admin, "private-code");
        int submissionId = jdbcTemplate.queryForObject("""
                INSERT INTO "Submission" (user_id, problem_id, language, code)
                VALUES (?, ?, 'python', 'print(42)')
                RETURNING id
                """, Integer.class, owner.id(), problemId);

        mockMvc.perform(get("/api/submissions/{id}", submissionId)
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/submissions/{id}", submissionId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.code").value("print(42)"));

        int exerciseId = createExercise(teacherOwner, "Private review exercise");
        int docSubmissionId = jdbcTemplate.queryForObject("""
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name)
                VALUES (?, ?, '/tmp/practice-platform-test-docs/private.docx', 'private.docx')
                RETURNING id
                """, Integer.class, owner.id(), exerciseId);

        mockMvc.perform(get("/api/office/docs/submissions/{id}", docSubmissionId)
                        .header("Authorization", bearer(teacherOther)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/office/docs/submissions/{id}", docSubmissionId)
                        .header("Authorization", bearer(teacherOwner)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanViewAllManagementCollections() throws Exception {
        TestUser admin = createUser("view_admin", "ADMIN");
        createProblem(admin, "admin-view-problem");
        createQuestion(admin, "Admin view question");
        createExercise(admin, "Admin view exercise");

        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/office/questions/manage").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/office/docs/exercises/manage").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/submissions").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    private TestUser createUser(String username, String role) throws Exception {
        JsonNode registered = register(username);
        int id = registered.path("user").path("id").asInt();
        if (!"USER".equals(role)) {
            jdbcTemplate.update("UPDATE \"User\" SET role=? WHERE id=?", role, id);
        }
        return new TestUser(id, username, login(username).path("token").asText());
    }

    private JsonNode register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int createProblem(TestUser user, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/problems")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemPayload(slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("problem").path("id").asInt();
    }

    private String problemPayload(String slug) {
        return """
                {
                  "slug":"%s",
                  "title":"%s",
                  "description":"integration test",
                  "inputFmt":"",
                  "outputFmt":"",
                  "difficulty":"EASY",
                  "timeLimit":1000,
                  "memoryLimit":256,
                  "tags":["test"],
                  "samples":[],
                  "testCases":[{"input":"","output":"1"}],
                  "visible":true
                }
                """.formatted(slug, slug);
    }

    private ObjectNode problemPayloadNode(String slug) throws Exception {
        return (ObjectNode) objectMapper.readTree(problemPayload(slug));
    }

    private int createQuestion(TestUser user, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/office/questions")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appType":"WORD",
                                  "category":"integration",
                                  "difficulty":"EASY",
                                  "questionType":"SINGLE_CHOICE",
                                  "content":"%s",
                                  "options":["A","B"],
                                  "answer":"0",
                                  "explanation":"test",
                                  "visible":true
                                }
                                """.formatted(content)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("question").path("id").asInt();
    }

    private int createExercise(TestUser user, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/office/docs/exercises")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"%s",
                                  "difficulty":"EASY",
                                  "description":"integration test",
                                  "visible":true
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("exercise").path("id").asInt();
    }

    private void setVisible(TestUser user, String path, boolean visible) throws Exception {
        mockMvc.perform(put(path)
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":" + visible + "}"))
                .andExpect(status().isOk());
    }

    private void assertTeacherDeleteBlocked(TestUser teacher, String path) throws Exception {
        mockMvc.perform(delete(path).header("Authorization", bearer(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("该内容已有学生提交，只能停用，不能彻底删除。"));
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private record TestUser(int id, String username, String token) {
    }
}
