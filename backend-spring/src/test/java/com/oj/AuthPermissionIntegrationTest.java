package com.oj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.common.JwtUtil;
import com.oj.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthPermissionIntegrationTest {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @AfterEach
    void cleanDatabase() {
        CurrentUser.clear();
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @Test
    void tokenVersionAndDatabaseRoleAreAuthoritative() throws Exception {
        TestUser teacher = createUser("token_teacher", "TEACHER");
        Claims claims = jwtUtil.verify(teacher.token());

        assertThat(claims.get("userId", Integer.class)).isEqualTo(teacher.id());
        assertThat(claims.get("username", String.class)).isEqualTo(teacher.username());
        assertThat(claims.get("role", String.class)).isEqualTo("TEACHER");
        assertThat(claims.get("tokenVersion", Integer.class)).isZero();

        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(teacher.token())))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE \"User\" SET role='USER' WHERE id=?", teacher.id());
        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(teacher.token())))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE \"User\" SET token_version=token_version+1 WHERE id=?", teacher.id());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(teacher.token())))
                .andExpect(status().isUnauthorized());

        JsonNode relogged = login(teacher.username(), PASSWORD, status().isOk());
        String freshToken = relogged.path("token").asText();
        assertThat(relogged.path("user").path("role").asText()).isEqualTo("USER");
        assertThat(jwtUtil.verify(freshToken).get("tokenVersion", Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(freshToken)))
                .andExpect(status().isOk());

        String forgedVersion = jwtUtil.sign(teacher.id(), teacher.username(), "ADMIN", 99);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(forgedVersion)))
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update("DELETE FROM \"User\" WHERE id=?", teacher.id());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(freshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesInvalidateOldTokensAndRequireCurrentAdmin() throws Exception {
        TestUser admin = createUser("role_admin", "ADMIN");
        TestUser target = createUser("role_target_v2", "USER");
        String oldUserToken = target.token();

        updateRole(admin.token(), target.id(), "TEACHER", 200);
        assertThat(tokenVersion(target.id())).isEqualTo(1);
        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(oldUserToken)))
                .andExpect(status().isUnauthorized());

        JsonNode teacherLogin = login(target.username(), PASSWORD, status().isOk());
        String teacherToken = teacherLogin.path("token").asText();
        assertThat(teacherLogin.path("user").path("role").asText()).isEqualTo("TEACHER");
        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        updateRole(admin.token(), target.id(), "USER", 200);
        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isUnauthorized());

        JsonNode userLogin = login(target.username(), PASSWORD, status().isOk());
        String currentUserToken = userLogin.path("token").asText();
        mockMvc.perform(get("/api/problems/manage").header("Authorization", bearer(currentUserToken)))
                .andExpect(status().isForbidden());
        updateRole(currentUserToken, admin.id(), "USER", 403);
    }

    @Test
    void passwordChangeInvalidatesEveryOldSession() throws Exception {
        TestUser user = createUser("password_user", "USER");
        String secondDeviceToken = login(user.username(), PASSWORD, status().isOk()).path("token").asText();

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"secret123","newPassword":"newSecret456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertThat(tokenVersion(user.id())).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(user.token())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(secondDeviceToken)))
                .andExpect(status().isUnauthorized());

        login(user.username(), PASSWORD, status().isUnauthorized());
        JsonNode newLogin = login(user.username(), "newSecret456", status().isOk());
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(newLogin.path("token").asText())))
                .andExpect(status().isOk());
    }

    @Test
    void lastAdministratorCannotBeDemotedOrDeletedAndDeletedUsersLoseAccess() throws Exception {
        TestUser firstAdmin = createUser("last_admin", "ADMIN");

        updateRole(firstAdmin.token(), firstAdmin.id(), "USER", 409);
        mockMvc.perform(delete("/api/users/{id}", firstAdmin.id())
                        .header("Authorization", bearer(firstAdmin.token())))
                .andExpect(status().isConflict());
        assertThat(adminCount()).isEqualTo(1);
        assertThat(tokenVersion(firstAdmin.id())).isZero();

        TestUser secondAdmin = createUser("second_admin", "ADMIN");
        updateRole(firstAdmin.token(), secondAdmin.id(), "TEACHER", 200);
        assertThat(adminCount()).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(secondAdmin.token())))
                .andExpect(status().isUnauthorized());

        TestUser victim = createUser("delete_victim", "USER");
        mockMvc.perform(delete("/api/users/{id}", victim.id())
                        .header("Authorization", bearer(firstAdmin.token())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(victim.token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentDemotionsCannotRemoveEveryAdministrator() throws Exception {
        TestUser first = createUser("concurrent_admin_one", "ADMIN");
        TestUser second = createUser("concurrent_admin_two", "ADMIN");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> firstResult = executor.submit(
                    () -> demoteAs(first, second.id(), start));
            Future<Integer> secondResult = executor.submit(
                    () -> demoteAs(second, first.id(), start));
            start.countDown();

            List<Integer> results = List.of(
                    firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(200, 409);
            assertThat(adminCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anonymousAccessUsesOnlyTheExplicitWhitelist() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO "Problem"
                    (slug, title, description, tags, samples, test_cases, visible)
                VALUES ('public-problem', 'Public', 'safe', '{}', '[]',
                        '[{"input":"","output":"1"}]', true)
                """);

        register("anonymous_register");
        login("anonymous_register", PASSWORD, status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/submissions/meta/languages")).andExpect(status().isOk());
        mockMvc.perform(get("/api/problems")).andExpect(status().isOk());
        mockMvc.perform(get("/api/problems/public-problem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problem.testCases").doesNotExist());
        mockMvc.perform(get("/api/users/leaderboard")).andExpect(status().isOk());

        for (String protectedPath : List.of(
                "/api/problems/manage",
                "/api/office/questions",
                "/api/office/docs/exercises",
                "/api/users",
                "/api/submissions",
                "/api/submissions/1",
                "/api/office/docs/exercises/1/teacher-doc",
                "/api/office/docs/submissions/1/download",
                "/api/not-whitelisted")) {
            mockMvc.perform(get(protectedPath)).andExpect(status().isUnauthorized());
        }
    }

    private int demoteAs(TestUser requester, int targetId, CountDownLatch start) throws Exception {
        CurrentUser.set(requester.id(), requester.username(), "ADMIN");
        try {
            start.await(5, TimeUnit.SECONDS);
            userService.updateRole(targetId, "USER");
            return 200;
        } catch (ApiException ex) {
            return ex.getStatus().value();
        } finally {
            CurrentUser.clear();
        }
    }

    private TestUser createUser(String username, String role) throws Exception {
        JsonNode registered = register(username);
        int id = registered.path("user").path("id").asInt();
        if (!"USER".equals(role)) {
            jdbcTemplate.update("UPDATE \"User\" SET role=? WHERE id=?", role, id);
        }
        String token = login(username, PASSWORD, status().isOk()).path("token").asText();
        return new TestUser(id, username, token);
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

    private JsonNode login(String username, String password,
                           org.springframework.test.web.servlet.ResultMatcher expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(expectedStatus)
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private void updateRole(String adminToken, int targetId, String role, int expectedStatus)
            throws Exception {
        mockMvc.perform(put("/api/users/{id}/role", targetId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private int tokenVersion(int userId) {
        return jdbcTemplate.queryForObject(
                "SELECT token_version FROM \"User\" WHERE id=?",
                Integer.class, userId);
    }

    private int adminCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"User\" WHERE role='ADMIN'",
                Integer.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(int id, String username, String token) {
    }
}
