package com.oj.runner.execution.linux;

public enum SandboxTermination {
    COMPLETED,
    TIME_LIMIT,
    MEMORY_LIMIT,
    OUTPUT_LIMIT,
    WORKSPACE_LIMIT,
    SANDBOX_ERROR
}
