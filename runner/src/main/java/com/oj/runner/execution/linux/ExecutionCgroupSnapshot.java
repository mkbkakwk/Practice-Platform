package com.oj.runner.execution.linux;

record ExecutionCgroupSnapshot(
        long memoryPeakBytes,
        long memoryMaxEvents,
        long oomEvents,
        long oomKillEvents,
        long pidsMaxEvents) {

    ExecutionCgroupSnapshot {
        if (memoryPeakBytes < 0 || memoryMaxEvents < 0 || oomEvents < 0
                || oomKillEvents < 0 || pidsMaxEvents < 0) {
            throw new IllegalArgumentException("cgroup counters must be non-negative");
        }
    }

    boolean indicatesMemoryLimit(int exitCode, long memoryLimitBytes) {
        if (oomKillEvents > 0 || oomEvents > 0) {
            return true;
        }
        long nearLimitThreshold = memoryLimitBytes - memoryLimitBytes / 20;
        return exitCode != 0 && memoryMaxEvents > 0 && memoryPeakBytes >= nearLimitThreshold;
    }
}
