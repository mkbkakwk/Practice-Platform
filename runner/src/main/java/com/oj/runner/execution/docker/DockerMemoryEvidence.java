package com.oj.runner.execution.docker;

record DockerMemoryEvidence(
        boolean oomKilled,
        long limitBytes,
        long peakBytes,
        long limitHitCount) {

    static DockerMemoryEvidence stateOnly(boolean oomKilled) {
        return new DockerMemoryEvidence(oomKilled, 0, 0, 0);
    }

    boolean exceededFor(int exitCode) {
        if (oomKilled) return true;
        if (exitCode == 0) return false;
        if (limitHitCount > 0) return true;
        return limitBytes > 0 && peakBytes >= limitBytes - limitBytes / 20;
    }
}
