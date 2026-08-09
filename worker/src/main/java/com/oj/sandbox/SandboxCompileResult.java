package com.oj.sandbox;

public record SandboxCompileResult(
        SandboxStatus status,
        Integer exitCode,
        String stderr,
        long timeMs,
        String message) {
}
