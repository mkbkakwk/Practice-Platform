package com.oj.sandbox;

public record SandboxLimits(
        long compileTimeMs,
        long runTimeMs,
        long memoryMb,
        int outputLimitBytes) {
}
