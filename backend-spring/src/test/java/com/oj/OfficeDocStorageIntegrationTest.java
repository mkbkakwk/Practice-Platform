package com.oj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.config.AppProperties;
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
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private Path storageRoot;

    @BeforeEach
    void resetState() throws Exception {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "OfficeDocSubmission", "OfficeRecord", "Submission",
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
                TRUNCATE TABLE "OfficeDocSubmission", "OfficeRecord", "Submission",
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
                "SELECT COUNT(*) FROM \"OfficeDocSubmission\"", Long.class)).isZero();
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
        mockMvc.perform(get("/api/office/docs/exercises/{id}/teacher-doc", outsideExercise))
                .andExpect(status().isNotFound());

        Path target = copyFixture("normal.docx", "symlink-target.docx");
        Path link = storageRoot.resolve("symlink.docx");
        Files.createSymbolicLink(link, target);
        int symlinkExercise = insertExercise(
                "Symlink path", teacher.id(), link.toString(), true);
        mockMvc.perform(get("/api/office/docs/exercises/{id}/teacher-doc", symlinkExercise))
                .andExpect(status().isNotFound());
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
