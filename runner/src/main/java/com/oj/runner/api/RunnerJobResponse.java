package com.oj.runner.api;

import java.util.List;

public record RunnerJobResponse(
        String requestId,
        RunnerCompileResult compile,
        List<RunnerCaseResult> cases,
        String message) {
}
