package com.oj.runner.api;

public record RunnerCaseResult(
        String caseId,
        RunnerStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        long timeMs,
        long memoryKb,
        String message) {
}
