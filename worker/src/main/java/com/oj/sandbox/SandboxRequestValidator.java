package com.oj.sandbox;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SandboxRequestValidator {

    private static final int MAX_CASES = 1_000;

    private SandboxRequestValidator() {
    }

    public static void validate(SandboxRequest request, int maxSourceBytes, int maxStdinBytes) {
        if (request == null) {
            throw new SandboxClientException("Sandbox request is required");
        }
        try {
            UUID.fromString(request.requestId());
        } catch (RuntimeException exception) {
            throw new SandboxClientException("Sandbox requestId must be a UUID");
        }
        if (request.language() == null) {
            throw new SandboxClientException("Sandbox language is required");
        }
        if (request.sourceCode() == null) {
            throw new SandboxClientException("Sandbox sourceCode is required");
        }
        if (utf8Length(request.sourceCode()) > maxSourceBytes) {
            throw new SandboxClientException("Sandbox sourceCode exceeds the configured limit");
        }
        SandboxLimits limits = request.limits();
        if (limits == null || limits.compileTimeMs() <= 0 || limits.runTimeMs() <= 0
                || limits.memoryMb() <= 0 || limits.outputLimitBytes() <= 0) {
            throw new SandboxClientException("Sandbox limits must be positive");
        }
        if (request.cases() == null || request.cases().isEmpty() || request.cases().size() > MAX_CASES) {
            throw new SandboxClientException("Sandbox cases must contain between 1 and " + MAX_CASES + " entries");
        }
        Set<String> caseIds = new HashSet<>();
        for (SandboxTestCase testCase : request.cases()) {
            if (testCase == null || testCase.caseId() == null
                    || !testCase.caseId().matches("[A-Za-z0-9._-]{1,64}")) {
                throw new SandboxClientException("Sandbox caseId is invalid");
            }
            if (!caseIds.add(testCase.caseId())) {
                throw new SandboxClientException("Sandbox caseId must be unique");
            }
            if (testCase.stdin() == null) {
                throw new SandboxClientException("Sandbox stdin is required");
            }
            if (utf8Length(testCase.stdin()) > maxStdinBytes) {
                throw new SandboxClientException("Sandbox stdin exceeds the configured limit");
            }
        }
    }

    public static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
