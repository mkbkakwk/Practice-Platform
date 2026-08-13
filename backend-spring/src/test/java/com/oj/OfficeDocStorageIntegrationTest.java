package com.oj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.config.AppProperties;
import com.oj.office.OfficeFileReconciler;
import com.oj.office.OfficeFileValidator;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfficeDocStorageIntegrationTest {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private OfficeFileReconciler fileReconciler;

    private Path storageRoot;

    @BeforeEach
    void resetState() throws Exception {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
        storageRoot = Path.of(appProperties.getDocStorage()).toAbsolutePath().normalize();
        assertThat(storageRoot.toString()).contains("practice-platform-test-docs");
        cleanStorage();
        Files.createDirectories(storageRoot);
    }

    @AfterEach
    void cleanTemporaryStorage() throws Exception {
        cleanStorage();
        Files.createDirectories(storageRoot);
        try (var files = Files.list(storageRoot)) {
            assertThat(files.findAny()).isEmpty();
        }
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @Test
    void failedUploadRemovesTheNewStudentFile() throws Exception {
        TestUser teacher = createUser("upload_teacher", "TEACHER");
        TestUser student = createUser("upload_student", "USER");
        Path reference = copyFixture("normal.docx", "teacher-reference.docx");
        int exerciseId = insertExercise(
                "Upload cleanup", teacher.id(), reference.toString(), true);

        MockMultipartFile damaged = new MockMultipartFile(
                "file", "damaged.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                fixtureBytes("damaged.docx"));

        mockMvc.perform(multipart("/api/office/docs/exercises/{id}/submit", exerciseId)
                        .file(damaged)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isBadRequest());

        try (var files = Files.list(storageRoot)) {
            assertThat(files.filter(Files::isRegularFile).toList())
                    .containsExactly(reference);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"OfficeDocSubmission\" WHERE status='FAILED'", Long.class)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT student_doc_path FROM \"OfficeDocSubmission\"", String.class)).isNull();

        MockMultipartFile normal = new MockMultipartFile(
                "file", "normal-after-malicious.docx", OfficeFileValidator.DOCX_CONTENT_TYPE,
                fixtureBytes("normal.docx"));
        mockMvc.perform(multipart("/api/office/docs/exercises/{id}/submit", exerciseId)
                        .file(normal)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"OfficeDocSubmission\" WHERE status='COMPLETED'", Long.class)).isOne();
    }

    @Test
    void validSubmissionPersistsDeterministicStructuredResultWithoutLeakingStoragePaths() throws Exception {
        TestUser teacher = createUser("result_teacher", "TEACHER");
        TestUser student = createUser("result_student", "USER");
        Path reference = copyFixture("normal.docx", "teacher-result-reference.docx");
        int exerciseId = insertExercise("Deterministic result", teacher.id(), reference.toString(), true);

        MockMultipartFile document = new MockMultipartFile(
                "file", "student.docx", OfficeFileValidator.DOCX_CONTENT_TYPE,
                fixtureBytes("normal.docx"));
        MvcResult submitted = mockMvc.perform(multipart("/api/office/docs/exercises/{id}/submit", exerciseId)
                        .file(document)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(submitted.getResponse().getContentAsString()).path("submission");

        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("score").asInt()).isEqualTo(100);
        assertThat(body.path("judgeVersion").asText()).isEqualTo("office-docx-v1");
        assertThat(body.path("resultDetail").path("passed").asBoolean()).isTrue();
        assertThat(body.has("studentDocPath")).isFalse();
        assertThat(body.toString()).doesNotContain(storageRoot.toString());
        String storageId = jdbcTemplate.queryForObject(
                "SELECT student_doc_path FROM \"OfficeDocSubmission\" WHERE id=?",
                String.class, body.path("id").asInt());
        assertThat(storageId).matches("[0-9a-f-]{36}\\.docx");
        assertThat(storageRoot.resolve(storageId)).exists();
    }

    @Test
    void studentCannotDownloadReferenceAndExerciseApiDoesNotLeakItsPath() throws Exception {
        TestUser teacher = createUser("reference_teacher", "TEACHER");
        TestUser student = createUser("reference_student", "USER");
        Path reference = copyFixture("normal.docx", "teacher-private-reference.docx");
        int exerciseId = insertExercise("Private reference", teacher.id(), reference.toString(), true);

        mockMvc.perform(get("/api/office/docs/exercises/{id}/teacher-doc", exerciseId)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        MvcResult exercise = mockMvc.perform(get("/api/office/docs/exercises/{id}", exerciseId)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(exercise.getResponse().getContentAsString())
                .doesNotContain("teacherDocPath")
                .doesNotContain(storageRoot.toString());
    }

    @Test
    void deleteCommitsDatabaseFirstThenRemovesOnlyOwnedFiles() throws Exception {
        TestUser admin = createUser("delete_admin", "ADMIN");
        TestUser student = createUser("delete_student", "USER");
        Path reference = copyFixture("normal.docx", "delete-reference.docx");
        Path studentDocument = copyFixture("normal.docx", "delete-student.docx");
        Path unrelated = copyFixture("normal.docx", "unrelated.docx");
        int exerciseId = insertExercise("Delete lifecycle", admin.id(), reference.toString(), true);
        jdbcTemplate.update("""
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name, status)
                VALUES (?, ?, ?, 'delete-student.docx', 'COMPLETED')
                """, student.id(), exerciseId, studentDocument.toString());

        mockMvc.perform(delete("/api/office/docs/exercises/{id}", exerciseId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"OfficeExercise\" WHERE id=?", Long.class, exerciseId)).isZero();
        assertThat(reference).doesNotExist();
        assertThat(studentDocument).doesNotExist();
        assertThat(unrelated).exists();
    }

    @Test
    void protectsStudentFilesAndRejectsOutsideAndSymbolicLinkPaths() throws Exception {
        TestUser teacher = createUser("storage_teacher", "TEACHER");
        TestUser owner = createUser("storage_owner", "USER");
        TestUser stranger = createUser("storage_other", "USER");

        Path studentFile = copyFixture("normal.docx", "student-private.docx");
        int exerciseId = insertExercise("Protected file", teacher.id(), null, true);
        int submissionId = jdbcTemplate.queryForObject("""
                INSERT INTO "OfficeDocSubmission"
                    (user_id, exercise_id, student_doc_path, student_doc_name)
                VALUES (?, ?, ?, 'student-private.docx')
                RETURNING id
                """, Integer.class, owner.id(), exerciseId, studentFile.toString());

        mockMvc.perform(get("/api/office/docs/submissions/{id}/download", submissionId)
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/office/docs/submissions/{id}/download", submissionId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        int outsideExercise = insertExercise(
                "Outside path", teacher.id(), "/tmp/outside-practice-platform.docx", true);
        mockMvc.perform(get("/api/office/docs/exercises/{id}/teacher-doc", outsideExercise)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isNotFound());

        Path target = copyFixture("normal.docx", "symlink-target.docx");
        Path link = storageRoot.resolve("symlink.docx");
        Files.createSymbolicLink(link, target);
        int symlinkExercise = insertExercise(
                "Symlink path", teacher.id(), link.toString(), true);
        mockMvc.perform(get("/api/office/docs/exercises/{id}/teacher-doc", symlinkExercise)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isNotFound());
    }

    @Test
    void twentyConcurrentSubmissionsCompleteWithoutCrossContamination() throws Exception {
        TestUser teacher = createUser("concurrent_teacher", "TEACHER");
        TestUser student = createUser("concurrent_student", "USER");
        Path reference = copyFixture("normal.docx", "concurrent-reference.docx");
        int exerciseId = insertExercise("Concurrent judging", teacher.id(), reference.toString(), true);
        byte[] document = fixtureBytes("normal.docx");

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Callable<JsonNode>> tasks = IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<JsonNode>) () -> {
                        MockMultipartFile upload = new MockMultipartFile(
                                "file", "student-" + index + ".docx",
                                OfficeFileValidator.DOCX_CONTENT_TYPE, document);
                        MvcResult result = mockMvc.perform(
                                        multipart("/api/office/docs/exercises/{id}/submit", exerciseId)
                                                .file(upload)
                                                .header("Authorization", bearer(student)))
                                .andExpect(status().isOk())
                                .andReturn();
                        return objectMapper.readTree(result.getResponse().getContentAsString())
                                .path("submission");
                    }).toList();
            var futures = tasks.stream().map(executor::submit).toList();
            List<JsonNode> submissions = futures.stream().map(future -> {
                try {
                    return future.get(60, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();

            assertThat(submissions).hasSize(20).allSatisfy(submission -> {
                assertThat(submission.path("status").asText()).isEqualTo("COMPLETED");
                assertThat(submission.path("score").asInt()).isEqualTo(100);
            });
            assertThat(new HashSet<>(submissions.stream().map(node -> node.path("id").asInt()).toList()))
                    .hasSize(20);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"OfficeDocSubmission\" WHERE status='COMPLETED'", Long.class))
                .isEqualTo(20);
        List<String> storageIds = jdbcTemplate.queryForList(
                "SELECT student_doc_path FROM \"OfficeDocSubmission\"", String.class);
        assertThat(storageIds).hasSize(20).doesNotHaveDuplicates();
        assertThat(storageIds).allSatisfy(storageId -> {
            assertThat(storageId).matches("[0-9a-f-]{36}\\.docx");
            assertThat(storageRoot.resolve(storageId)).exists();
        });
    }

    @Test
    void orphanReconcilerDeletesOnlyOldUnreferencedManagedFiles() throws Exception {
        TestUser teacher = createUser("reconcile_teacher", "TEACHER");
        String referencedId = java.util.UUID.randomUUID() + ".docx";
        String orphanId = java.util.UUID.randomUUID() + ".docx";
        Path referenced = Files.write(storageRoot.resolve(referencedId), fixtureBytes("normal.docx"));
        Path orphan = Files.write(storageRoot.resolve(orphanId), fixtureBytes("normal.docx"));
        FileTime old = FileTime.from(Instant.now().minusSeconds(7200));
        Files.setLastModifiedTime(referenced, old);
        Files.setLastModifiedTime(orphan, old);
        insertExercise("Referenced file", teacher.id(), referencedId, true);

        fileReconciler.removeOldUnreferencedManagedFiles();

        assertThat(referenced).exists();
        assertThat(orphan).doesNotExist();
    }

    private int insertExercise(String title, int createdBy, String teacherPath, boolean visible) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO "OfficeExercise"
                    (title, description, teacher_doc_path, teacher_doc_name, visible, created_by)
                VALUES (?, 'integration test', ?, 'reference.docx', ?, ?)
                RETURNING id
                """, Integer.class, title, teacherPath, visible, createdBy);
    }

    private TestUser createUser(String username, String role) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        int id = objectMapper.readTree(registered.getResponse().getContentAsString())
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
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("token").asText();
        return new TestUser(id, token);
    }

    private byte[] fixtureBytes(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/docx/" + name)) {
            assertThat(input).as("fixture %s", name).isNotNull();
            return input.readAllBytes();
        }
    }

    private Path copyFixture(String fixture, String filename) throws Exception {
        Path target = storageRoot.resolve(filename);
        Files.write(target, fixtureBytes(fixture));
        return target;
    }

    private void cleanStorage() throws Exception {
        if (storageRoot == null || !Files.exists(storageRoot)) {
            return;
        }
        try (var paths = Files.walk(storageRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(storageRoot)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private record TestUser(int id, String token) {
    }
}
