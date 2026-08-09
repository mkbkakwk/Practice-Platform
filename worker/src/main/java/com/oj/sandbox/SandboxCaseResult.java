package com.oj.sandbox;

public record SandboxCaseResult(
        String caseId,
        SandboxStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        long timeMs,
        long memoryKb,
        String message) {
}
