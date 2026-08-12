package com.oj.runner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunnerFailClosedTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void defaultExecutorReturnsControlledSystemErrorWithoutExecutingCode() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", "Bearer test-runner-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compile.status").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.compile.message").value("Sandbox executor unavailable"))
                .andExpect(jsonPath("$.cases.length()").value(0));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.sandboxAvailable").value(false));
    }

    @Test
    void runnerMainSourcesContainNoStudentProcessExecution() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var files = Files.walk(sourceRoot)) {
            String allSources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .reduce("", String::concat);
            assertThat(allSources).doesNotContain(
                    "ProcessBuilder", "withPrivileged(true)", "seccomp=unconfined", "nsjail");
        }
    }

    @Test
    void dockerControlPlaneNeverPassesItsSocketOrHostNamespacesToStudents() throws Exception {
        String source = Files.readString(Path.of("src", "main", "java", "com", "oj", "runner",
                "execution", "docker", "DockerSandboxExecutor.java"));
        assertThat(source)
                .contains("withNetworkMode(\"none\")", "withReadonlyRootfs(true)",
                        "withPrivileged(false)", "withCapDrop(Capability.values())",
                        "withPidsLimit(properties.getPidsLimit())", "withUser(SANDBOX_USER)")
                .doesNotContain("docker.sock", "withPidMode(\"host\")", "withIpcMode(\"host\")",
                        "withNetworkMode(\"host\")", "withCapAdd", "seccomp=unconfined");
    }

    private String validRequest() {
        return """
                {"requestId":"11111111-1111-4111-8111-111111111111","language":"PYTHON",
                "sourceCode":"print(1)","limits":{"compileTimeMs":10000,"runTimeMs":1000,
                "memoryMb":256,"outputLimitBytes":1024},"cases":[{"caseId":"1","stdin":""}]}
                """;
    }
}
