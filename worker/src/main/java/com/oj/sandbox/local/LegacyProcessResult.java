package com.oj.sandbox.local;

public record LegacyProcessResult(
        String stdout,
        String stderr,
        int exitCode,
        boolean timedOut,
        boolean memoryError,
        boolean outputExceeded,
        long elapsedMs) {
}
