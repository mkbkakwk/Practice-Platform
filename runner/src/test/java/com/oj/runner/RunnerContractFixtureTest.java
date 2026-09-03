package com.oj.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestFixtureMatchesTheRunnerDto() throws Exception {
        JsonNode fixture = read("valid-job-request.json");
        RunnerJobRequest request = objectMapper.treeToValue(fixture, RunnerJobRequest.class);

        assertThat(request.language().name()).isEqualTo("CPP17");
        assertThat(request.cases()).hasSize(1);
        JsonNode serialized = objectMapper.valueToTree(request);
        assertThat(serialized.toString()).isEqualTo(fixture.toString());
        assertThat(fixture.has("command")).isFalse();
        assertThat(fixture.toString()).doesNotContain("expectedOutput");
    }

    @Test
    void responseFixturesMatchTheRunnerDto() throws Exception {
        List<String> fixtures = List.of(
                "valid-job-response.json",
                "compile-error-response.json",
                "runtime-error-response.json",
                "timeout-response.json");

        for (String fixtureName : fixtures) {
            JsonNode fixture = read(fixtureName);
            RunnerJobResponse response = objectMapper.treeToValue(fixture, RunnerJobResponse.class);
            assertThat(response.requestId()).isNotBlank();
            assertThat(List.of(RunnerStatus.values())).contains(response.compile().status());
            JsonNode serialized = objectMapper.valueToTree(response);
            assertThat(serialized.toString()).isEqualTo(fixture.toString());
        }
    }

    private JsonNode read(String name) throws Exception {
        return objectMapper.readTree(Files.readString(fixtureDirectory().resolve(name)));
    }

    private Path fixtureDirectory() {
        String configured = System.getenv("RUNNER_CONTRACT_FIXTURE_DIR");
        return configured == null || configured.isBlank()
                ? Path.of("..", "test", "fixtures", "runner")
                : Path.of(configured);
    }
}
