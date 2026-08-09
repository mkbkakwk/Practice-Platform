package com.oj.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.judge.JudgeService;
import com.oj.sandbox.local.LegacyLocalSandboxClient;
import com.oj.sandbox.remote.RemoteSandboxClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Locale;

@Configuration
public class SandboxConfiguration {

    @Bean
    public SandboxClient sandboxClient(
            @Value("${oj.judge.execution-mode:legacy-local}") String executionMode,
            @Value("${oj.judge.workspace:/tmp/oj-judge}") String workspace,
            @Value("${oj.judge.max-source-bytes:1048576}") int maxSourceBytes,
            @Value("${oj.judge.max-stdin-bytes:1048576}") int maxStdinBytes,
            @Value("${oj.judge.runner.base-url:}") String runnerBaseUrl,
            @Value("${oj.judge.runner.token:}") String runnerToken,
            @Value("${oj.judge.runner.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${oj.judge.runner.read-timeout-ms:15000}") long readTimeoutMs,
            @Value("${oj.judge.runner.max-request-bytes:4194304}") int maxRequestBytes,
            @Value("${oj.judge.runner.max-response-bytes:33554432}") int maxResponseBytes) {
        String normalizedMode = executionMode == null ? "" : executionMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedMode) {
            case "legacy-local" -> new LegacyLocalSandboxClient(
                    Path.of(workspace), maxSourceBytes, maxStdinBytes);
            case "remote" -> new RemoteSandboxClient(
                    requireRemoteValue(runnerBaseUrl, "RUNNER_BASE_URL"),
                    requireRemoteValue(runnerToken, "RUNNER_TOKEN"),
                    connectTimeoutMs, readTimeoutMs, maxSourceBytes, maxStdinBytes,
                    maxRequestBytes, maxResponseBytes, new ObjectMapper());
            default -> throw new IllegalStateException(
                    "Unsupported JUDGE_EXECUTION_MODE: " + executionMode);
        };
    }

    @Bean
    public JudgeService judgeService(
            SandboxClient sandboxClient,
            @Value("${oj.judge.compile-timeout-ms:10000}") long compileTimeoutMs,
            @Value("${oj.judge.output-limit-bytes:16777216}") int outputLimitBytes) {
        return new JudgeService(sandboxClient, compileTimeoutMs, outputLimitBytes);
    }

    private String requireRemoteValue(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required in remote execution mode");
        }
        return value.trim();
    }
}
