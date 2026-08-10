package com.oj.runner.api;

public record RunnerCompileResult(
        RunnerStatus status,
        Integer exitCode,
        String stderr,
        long timeMs,
        String message) {
}
