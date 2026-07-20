package com.oj.judge;

/**
 * Result of a single program execution against one test case.
 */
public class RunResult {
    public final boolean ok;
    public final String stdout;
    public final String stderr;
    public final int exitCode;
    public final boolean timedOut;
    public final boolean memoryError;
    public final long elapsedMs;

    public RunResult(boolean ok, String stdout, String stderr, int exitCode,
                     boolean timedOut, boolean memoryError, long elapsedMs) {
        this.ok = ok;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.memoryError = memoryError;
        this.elapsedMs = elapsedMs;
    }
}
