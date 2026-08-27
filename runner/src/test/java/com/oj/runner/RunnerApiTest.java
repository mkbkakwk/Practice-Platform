package com.oj.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oj.runner.api.RunnerCaseResult;
import com.oj.runner.api.RunnerCompileResult;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.execution.SandboxExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RunnerApiTest {

    private static final String TOKEN = "test-runner-token";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    SandboxExecutor sandboxExecutor;

    @BeforeEach
    void defaultExecutorResponse() {
        doReturn(false).when(sandboxExecutor).available();
        doAnswer(invocation -> {
            var job = invocation.getArgument(0, com.oj.runner.language.RunnerJob.class);
            return response(job.request().requestId(), RunnerStatus.OK);
        }).when(sandboxExecutor).execute(ArgumentMatchers.any());
    }

    @Test
    void healthIsMinimalAndDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.sandboxAvailable").value(false));
    }

    @Test
    void readinessFailsClosedWhenSandboxIsUnavailableWhileLivenessRemainsUp() throws Exception {
        mockMvc.perform(get("/api/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void readinessIsUpWhenTheExistingSandboxCapabilityIsAvailable() throws Exception {
        doReturn(true).when(sandboxExecutor).available();

        mockMvc.perform(get("/api/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void missingAuthorizationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void incorrectAuthorizationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Runner authentication failed"));
    }

    @Test
    void correctAuthorizationAcceptsAValidJob() throws Exception {
        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("11111111-1111-4111-8111-111111111111"))
                .andExpect(jsonPath("$.compile.status").value("OK"))
                .andExpect(jsonPath("$.cases[0].status").value("OK"));
    }

    @Test
    void invalidUuidReturns400() throws Exception {
        ObjectNode request = validRequest();
        request.put("requestId", "not-a-uuid");
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void unknownLanguageReturns400() throws Exception {
        ObjectNode request = validRequest();
        request.put("language", "RUBY");
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void arbitraryCommandFieldIsRejected() throws Exception {
        ObjectNode request = validRequest();
        request.put("command", "sh -c arbitrary");
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void oversizedSourceReturns400() throws Exception {
        ObjectNode request = validRequest();
        request.put("sourceCode", "x".repeat(1_025));
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void oversizedStdinReturns400() throws Exception {
        ObjectNode request = validRequest();
        ((ObjectNode) request.withArray("cases").get(0)).put("stdin", "x".repeat(1_025));
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void invalidLimitsReturn400() throws Exception {
        for (String field : List.of("compileTimeMs", "runTimeMs", "memoryMb", "outputLimitBytes")) {
            ObjectNode request = validRequest();
            ((ObjectNode) request.get("limits")).put(field, 0);
            perform(request).andExpect(status().isBadRequest());
        }

        ObjectNode excessive = validRequest();
        ((ObjectNode) excessive.get("limits")).put("memoryMb", 1_025);
        perform(excessive).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCaseIdReturns400() throws Exception {
        ObjectNode request = validRequest();
        ArrayNode cases = request.withArray("cases");
        cases.add(objectMapper.createObjectNode().put("caseId", "1").put("stdin", ""));
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void emptyCasesReturn400() throws Exception {
        ObjectNode request = validRequest();
        request.set("cases", objectMapper.createArrayNode());
        perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void oversizedHttpBodyReturns413BeforeDtoValidation() throws Exception {
        String body = "{\"padding\":\"" + "x".repeat(4_097) + "\"}";
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @ParameterizedTest
    @EnumSource(value = RunnerStatus.class, names = {
            "OK", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED",
            "MEMORY_LIMIT_EXCEEDED", "OUTPUT_LIMIT_EXCEEDED"})
    void returnsStructuredExecutorStatuses(RunnerStatus status) throws Exception {
        doReturn(response("11111111-1111-4111-8111-111111111111", status))
                .when(sandboxExecutor).execute(ArgumentMatchers.any());

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath(status == RunnerStatus.COMPILE_ERROR
                        ? "$.compile.status" : "$.cases[0].status").value(status.name()));
    }

    @Test
    void executorExceptionBecomesControlledSystemError() throws Exception {
        doThrow(new IllegalStateException("internal test detail"))
                .when(sandboxExecutor).execute(ArgumentMatchers.any());

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compile.status").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.compile.message").value("Sandbox executor failed"));
    }

    @Test
    void executorOutputBeyondTheRequestedLimitBecomesControlledSystemError() throws Exception {
        RunnerJobResponse oversized = new RunnerJobResponse(
                "11111111-1111-4111-8111-111111111111",
                new RunnerCompileResult(RunnerStatus.OK, 0, "", 1, ""),
                List.of(new RunnerCaseResult(
                        "1", RunnerStatus.OK, 0, "x".repeat(1_025), "", 1, 0, "")),
                "");
        doReturn(oversized).when(sandboxExecutor).execute(ArgumentMatchers.any());

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compile.status").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.compile.message").value("Sandbox executor failed"));
    }

    @Test
    void boundedOutputCanUseTheFullLimitWithoutCountingControlledMetadata() throws Exception {
        RunnerJobResponse bounded = new RunnerJobResponse(
                "11111111-1111-4111-8111-111111111111",
                new RunnerCompileResult(RunnerStatus.OK, 0, "", 1, ""),
                List.of(new RunnerCaseResult(
                        "1", RunnerStatus.OUTPUT_LIMIT_EXCEEDED, -1,
                        "x".repeat(1_024), "", 1, 0, "Output limit exceeded")),
                "");
        doReturn(bounded).when(sandboxExecutor).execute(ArgumentMatchers.any());

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].status").value("OUTPUT_LIMIT_EXCEEDED"));
    }

    @Test
    void oversizedExecutorMetadataBecomesControlledSystemError() throws Exception {
        RunnerJobResponse oversized = new RunnerJobResponse(
                "11111111-1111-4111-8111-111111111111",
                new RunnerCompileResult(RunnerStatus.OK, 0, "", 1, ""),
                List.of(new RunnerCaseResult(
                        "1", RunnerStatus.RUNTIME_ERROR, 1, "", "", 1, 0,
                        "x".repeat(1_025))),
                "");
        doReturn(oversized).when(sandboxExecutor).execute(ArgumentMatchers.any());

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compile.status").value("SYSTEM_ERROR"));
    }

    @Test
    void concurrentJobBeyondTheConfiguredLimitReturns429() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test executor was not released");
            }
            var job = invocation.getArgument(0, com.oj.runner.language.RunnerJob.class);
            return response(job.request().requestId(), RunnerStatus.OK);
        }).when(sandboxExecutor).execute(ArgumentMatchers.any());

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
            try {
                perform(validRequest()).andExpect(status().isOk());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        ObjectNode second = validRequest();
        second.put("requestId", UUID.randomUUID().toString());
        perform(second)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RUNNER_BUSY"));

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
    }

    @Test
    void logsDoNotContainTokenSourceOrInput(CapturedOutput output) throws Exception {
        ObjectNode request = validRequest();
        String sourceSentinel = "source-secret-sentinel";
        String inputSentinel = "input-secret-sentinel";
        request.put("sourceCode", sourceSentinel);
        ((ObjectNode) request.withArray("cases").get(0)).put("stdin", inputSentinel);

        perform(request).andExpect(status().isOk());

        assertThat(output.getAll())
                .doesNotContain(TOKEN, sourceSentinel, inputSentinel, "Authorization: Bearer");
    }

    private org.springframework.test.web.servlet.ResultActions perform(JsonNode request) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs")
                .header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()));
    }

    private ObjectNode validRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("requestId", "11111111-1111-4111-8111-111111111111");
        request.put("language", "PYTHON");
        request.put("sourceCode", "print(1)");
        request.set("limits", objectMapper.createObjectNode()
                .put("compileTimeMs", 10_000)
                .put("runTimeMs", 1_000)
                .put("memoryMb", 256)
                .put("outputLimitBytes", 1_024));
        request.set("cases", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("caseId", "1").put("stdin", "")));
        return request;
    }

    private RunnerJobResponse response(String requestId, RunnerStatus status) {
        if (status == RunnerStatus.COMPILE_ERROR) {
            return new RunnerJobResponse(
                    requestId,
                    new RunnerCompileResult(status, 1, "compile failed", 1, "Compilation failed"),
                    List.of(),
                    "Compilation failed");
        }
        RunnerCompileResult compile = new RunnerCompileResult(RunnerStatus.OK, 0, "", 1, "");
        RunnerCaseResult caseResult = new RunnerCaseResult(
                "1", status, status == RunnerStatus.OK ? 0 : 1,
                status == RunnerStatus.OK ? "1\n" : "", "", 1, 0, "");
        return new RunnerJobResponse(requestId, compile, List.of(caseResult), "");
    }
}
