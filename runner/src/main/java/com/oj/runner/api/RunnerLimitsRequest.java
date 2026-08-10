package com.oj.runner.api;

public record RunnerLimitsRequest(
        long compileTimeMs,
        long runTimeMs,
        long memoryMb,
        int outputLimitBytes) {
}
