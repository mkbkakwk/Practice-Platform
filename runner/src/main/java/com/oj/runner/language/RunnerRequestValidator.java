package com.oj.runner.language;

import com.oj.runner.api.RunnerCaseRequest;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerLimitsRequest;
import com.oj.runner.config.RunnerProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RunnerRequestValidator {

    private static final Pattern REQUEST_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
    private static final Pattern CASE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final RunnerProperties properties;
    private final LanguageProfileRegistry profileRegistry;

    public RunnerRequestValidator(RunnerProperties properties, LanguageProfileRegistry profileRegistry) {
        this.properties = properties;
        this.profileRegistry = profileRegistry;
    }

    public RunnerJob validate(RunnerJobRequest request) {
        if (request == null) {
            throw invalid("Runner request is required");
        }
        validateRequestId(request.requestId());
        LanguageProfile profile = profileRegistry.require(request.language());
        if (request.sourceCode() == null) {
            throw invalid("Runner sourceCode is required");
        }
        if (utf8Length(request.sourceCode()) > properties.getMaxSourceBytes()) {
            throw invalid("Runner sourceCode exceeds the configured limit");
        }
        validateLimits(request.limits());
        validateCases(request);
        return new RunnerJob(request, profile);
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw invalid("Runner requestId must be a canonical UUID");
        }
        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException exception) {
            throw invalid("Runner requestId must be a canonical UUID");
        }
    }

    private void validateLimits(RunnerLimitsRequest limits) {
        if (limits == null) {
            throw invalid("Runner limits are required");
        }
        if (limits.compileTimeMs() <= 0 || limits.compileTimeMs() > properties.getMaxCompileTimeMs()) {
            throw invalid("Runner compileTimeMs is outside the configured range");
        }
        if (limits.runTimeMs() <= 0 || limits.runTimeMs() > properties.getMaxRunTimeMs()) {
            throw invalid("Runner runTimeMs is outside the configured range");
        }
        if (limits.memoryMb() <= 0 || limits.memoryMb() > properties.getMaxMemoryMb()) {
            throw invalid("Runner memoryMb is outside the configured range");
        }
        if (limits.outputLimitBytes() <= 0
                || limits.outputLimitBytes() > properties.getMaxOutputLimitBytes()) {
            throw invalid("Runner outputLimitBytes is outside the configured range");
        }
    }

    private void validateCases(RunnerJobRequest request) {
        if (request.cases() == null || request.cases().isEmpty()
                || request.cases().size() > properties.getMaxCases()) {
            throw invalid("Runner cases are outside the configured range");
        }
        Set<String> caseIds = new HashSet<>();
        for (RunnerCaseRequest testCase : request.cases()) {
            if (testCase == null || testCase.caseId() == null
                    || !CASE_ID.matcher(testCase.caseId()).matches()) {
                throw invalid("Runner caseId is invalid");
            }
            if (!caseIds.add(testCase.caseId())) {
                throw invalid("Runner caseId must be unique");
            }
            if (testCase.stdin() == null) {
                throw invalid("Runner stdin is required");
            }
            if (utf8Length(testCase.stdin()) > properties.getMaxStdinBytes()) {
                throw invalid("Runner stdin exceeds the configured limit");
            }
        }
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private RunnerRequestValidationException invalid(String message) {
        return new RunnerRequestValidationException(message);
    }
}
