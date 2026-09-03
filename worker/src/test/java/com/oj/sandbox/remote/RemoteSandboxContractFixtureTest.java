package com.oj.sandbox.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteSandboxContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestFixtureMatchesTheWorkerDto() throws Exception {
        JsonNode fixture = read("valid-job-request.json");
        SandboxRequest request = objectMapper.treeToValue(fixture, SandboxRequest.class);

        assertThat(request.language().name()).isEqualTo("CPP17");
        assertThat(request.cases()).hasSize(1);
        JsonNode serialized = objectMapper.valueToTree(request);
        assertThat(serialized.toString()).isEqualTo(fixture.toString());
        assertThat(fixture.has("command")).isFalse();
        assertThat(fixture.toString()).doesNotContain("expectedOutput");
    }

    @Test
    void responseFixturesMatchTheWorkerDto() throws Exception {
        List<String> fixtures = List.of(
                "valid-job-response.json",
                "compile-error-response.json",
                "runtime-error-response.json",
                "timeout-response.json");

        for (String fixtureName : fixtures) {
            JsonNode fixture = read(fixtureName);
            SandboxResult response = objectMapper.treeToValue(fixture, SandboxResult.class);
            assertThat(List.of(SandboxStatus.values())).contains(response.compile().status());
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
