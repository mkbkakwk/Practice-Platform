package com.oj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.dto.AuthResponse;
import com.oj.dto.RegisterRequest;
import com.oj.service.AuthService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "oj.promote-first-admin=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirstAdminPromotionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", rejudge_batch_item, rejudge_batch, algorithm_judge_history, "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "ContestProblem", "ContestParticipant", "Contest",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE "judge_outbox", rejudge_batch_item, rejudge_batch, algorithm_judge_history, "OfficeDocSubmission", "OfficeRecord", "Submission",
                    "ContestProblem", "ContestParticipant", "Contest",
                    "OfficeExercise", "OfficeQuestion", "Problem", "User" RESTART IDENTITY
                """);
    }


    @Test
    void promotesOnlyTheFirstPublicRegistrationWhenEnabled() throws Exception {
        JsonNode first = register("first_admin");
        JsonNode second = register("second_user");

        assertThat(first.path("user").path("role").asText()).isEqualTo("ADMIN");
        assertThat(second.path("user").path("role").asText()).isEqualTo("USER");
    }


    @Test
    void concurrentRegistrationsCreateExactlyOneFirstAdministrator() throws Exception {
        int registrations = 4;
        ExecutorService executor = Executors.newFixedThreadPool(registrations);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < registrations; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    RegisterRequest request = new RegisterRequest();
                    request.setUsername("concurrent_first_" + index);
                    request.setPassword("secret123");
                    start.await(5, TimeUnit.SECONDS);
                    AuthResponse response = authService.register(request);
                    return response.getUser().getRole();
                }));
            }
            start.countDown();

            List<String> roles = new ArrayList<>();
            for (Future<String> future : futures) {
                roles.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(roles).containsOnly("ADMIN", "USER");
            assertThat(roles.stream().filter("ADMIN"::equals).count()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM \"User\" WHERE role='ADMIN'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private JsonNode register(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
