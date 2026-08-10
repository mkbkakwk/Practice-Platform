package com.oj.sandbox.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP contract check against the disposable Runner service. */
class RemoteSandboxRunnerContractIT {

    @Test
    void workerClientCallsTheAuthenticatedRunnerControllerOverHttp() throws Exception {
        String baseUrl = requiredEnvironment("RUNNER_CONTRACT_BASE_URL");
        String token = requiredEnvironment("RUNNER_CONTRACT_TOKEN");
        ObjectMapper objectMapper = new ObjectMapper();
        SandboxRequest request = objectMapper.readValue(
                Files.readString(fixtureDirectory().resolve("valid-job-request.json")),
                SandboxRequest.class);
        RemoteSandboxClient client = new RemoteSandboxClient(
                baseUrl, token, 2_000, 10_000,
                1_048_576, 1_048_576, 4_194_304, 4_194_304, objectMapper);

        SandboxResult response = client.execute(request);

        assertThat(response.requestId()).isEqualTo(request.requestId());
        assertThat(response.compile().status()).isEqualTo(SandboxStatus.OK);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().caseId()).isEqualTo("1");
        assertThat(response.cases().getFirst().status()).isEqualTo(SandboxStatus.OK);
        assertThat(response.cases().getFirst().stdout()).isEqualTo("contract-ok\n");
    }

    private Path fixtureDirectory() {
        return Path.of(requiredEnvironment("RUNNER_CONTRACT_FIXTURE_DIR"));
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the Runner contract test");
        }
        return value;
    }
}
