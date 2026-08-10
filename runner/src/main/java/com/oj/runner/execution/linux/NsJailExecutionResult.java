package com.oj.runner.execution.linux;

public record NsJailExecutionResult(
        SandboxTermination termination,
        int exitCode,
        String stdout,
        String stderr,
        long timeMs,
        long memoryKb,
        String diagnostic) {

    public static NsJailExecutionResult sandboxError(String diagnostic) {
        return new NsJailExecutionResult(
                SandboxTermination.SANDBOX_ERROR, -1, "", "", 0, 0, diagnostic);
    }
}
