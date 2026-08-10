package com.oj.runner.api;

import java.util.List;

public record RunnerJobRequest(
        String requestId,
        RunnerLanguage language,
        String sourceCode,
        RunnerLimitsRequest limits,
        List<RunnerCaseRequest> cases) {
}
