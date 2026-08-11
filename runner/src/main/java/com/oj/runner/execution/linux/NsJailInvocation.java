package com.oj.runner.execution.linux;

import java.nio.file.Path;
import java.util.List;

public record NsJailInvocation(
        SandboxPhase phase,
        Path config,
        Path log,
        Path workspace,
        ExecutionCgroupLease executionCgroup,
        List<String> argv,
        byte[] stdin,
        long wallTimeMs,
        long memoryLimitMb,
        int outputLimitBytes) {

    public NsJailInvocation {
        argv = List.copyOf(argv);
        stdin = stdin.clone();
    }

    @Override
    public byte[] stdin() {
        return stdin.clone();
    }
}
